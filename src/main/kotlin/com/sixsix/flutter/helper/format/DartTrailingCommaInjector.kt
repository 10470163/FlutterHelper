package com.sixsix.flutter.helper.format

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.DartTokenTypes
import com.jetbrains.lang.dart.psi.DartArguments
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartListLiteralExpression
import com.jetbrains.lang.dart.psi.DartSetOrMapLiteralExpression
import com.sixsix.flutter.helper.settings.FlutterHelperSettings

/**
 * Pre-processor for Reformat Code: import style → quote normalize → trailing commas.
 * 格式化（Reformat Code）前处理器：import 风格重写 → 双引号转单引号 → 尾随逗号。
 *
 * Runs only on reformat; save alone does not rewrite source.
 * 仅在此触发，保存时不改写源码。
 *
 * After Document edits, [commitDocument] is required so Formatter / dartfmt see a committed doc.
 * Document 改动后必须 [commitDocument]，否则 Formatter / dartfmt 会因未提交文档失败。
 */
class DartCodeStylePreFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        val settings = FlutterHelperSettings.getInstance()
        if (!settings.enableImportRewrite && !settings.enableQuoteNormalize && !settings.enableTrailingCommas) {
            return range
        }

        val psi = element.psi
        if (psi.language != DartLanguage.INSTANCE) return range
        val file = psi.containingFile as? DartFile ?: return range
        val document = file.viewProvider.document ?: return range
        val project = file.project
        val manager = PsiDocumentManager.getInstance(project)

        manager.doPostponedOperationsAndUnblockDocument(document)
        manager.commitDocument(document)
        val psiFile = manager.getPsiFile(document) as? DartFile ?: return range

        if (settings.enableImportRewrite) {
            DartImportStyleProcessor.rewrite(psiFile)
            manager.commitDocument(document)
        }
        if (settings.enableQuoteNormalize) {
            val current = manager.getPsiFile(document) as? DartFile ?: return range
            DartQuoteNormalizer.normalize(current)
            manager.doPostponedOperationsAndUnblockDocument(document)
            manager.commitDocument(document)
        }
        if (settings.enableTrailingCommas) {
            val current = manager.getPsiFile(document) as? DartFile ?: return range
            DartTrailingCommaInjector.inject(current, range)
            manager.commitDocument(document)
        }

        val committed = manager.getPsiFile(document) ?: file
        return TextRange(range.startOffset, committed.textLength.coerceAtLeast(range.endOffset))
    }
}

/**
 * Inserts trailing commas before closing `)` / `]` / `}` of multiline calls and collections.
 * 多行调用/集合闭合括号前补尾随逗号；不处理形参列表。
 *
 * Skips `setState` calls (avoids `setState(() { ... },)`).
 * 忽略 [setState] 调用（避免 `setState(() { ... },)`）。
 *
 * Optional [range] limits the scan region.
 * 可选 [range] 限制扫描范围。
 */
object DartTrailingCommaInjector {

    fun inject(file: PsiFile, range: TextRange? = null) {
        val document = file.viewProvider.document ?: return
        val project = file.project
        val manager = PsiDocumentManager.getInstance(project)
        manager.doPostponedOperationsAndUnblockDocument(document)
        manager.commitDocument(document)

        val psiFile = manager.getPsiFile(document) ?: return
        val fileText = document.text
        val insertions = mutableListOf<Int>()

        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (range != null && !range.intersects(element.textRange)) {
                    super.visitElement(element)
                    return
                }
                when (element) {
                    is DartArguments -> {
                        if (!isSetStateCall(element)) {
                            collectBeforeClosingToken(element, DartTokenTypes.RPAREN, ')', fileText, insertions)
                        }
                    }
                    is DartListLiteralExpression ->
                        collectBeforeClosingToken(element, DartTokenTypes.RBRACKET, ']', fileText, insertions)
                    is DartSetOrMapLiteralExpression ->
                        collectBeforeClosingToken(element, DartTokenTypes.RBRACE, '}', fileText, insertions)
                }
                super.visitElement(element)
            }
        })

        if (insertions.isEmpty()) return

        for (offset in insertions.distinct().sortedDescending()) {
            if (offset in 0..document.textLength) {
                document.insertString(offset, ",")
            }
        }
        manager.commitDocument(document)
    }

    /** `setState(...)` / `this.setState(...)` / `foo.setState(...)` */
    private fun isSetStateCall(arguments: DartArguments): Boolean {
        val call = arguments.parent as? DartCallExpression ?: return false
        return isSetStateCallee(call.expression?.text)
    }

    internal fun isSetStateCallee(calleeText: String?): Boolean {
        val callee = calleeText?.trim() ?: return false
        return callee == "setState" || callee.endsWith(".setState")
    }

    private fun collectBeforeClosingToken(
        element: PsiElement,
        closingType: com.intellij.psi.tree.IElementType,
        closingChar: Char,
        fileText: String,
        insertions: MutableList<Int>,
    ) {
        if (!element.text.contains('\n')) return

        val closingOffset = element.node?.findChildByType(closingType)?.startOffset
            ?: run {
                val end = element.textRange.endOffset
                if (end > 0 && fileText.getOrNull(end - 1) == closingChar) end - 1 else null
            }
            ?: return

        val beforeClosing = skipWhitespaceBackward(fileText, closingOffset - 1)
        if (beforeClosing < 0) return
        val ch = fileText[beforeClosing]
        if (ch == ',' || ch == '(' || ch == '[' || ch == '{') return

        insertions.add(beforeClosing + 1)
    }

    private fun skipWhitespaceBackward(text: String, start: Int): Int {
        var i = start
        while (i >= 0 && text[i].isWhitespace()) i--
        return i
    }
}
