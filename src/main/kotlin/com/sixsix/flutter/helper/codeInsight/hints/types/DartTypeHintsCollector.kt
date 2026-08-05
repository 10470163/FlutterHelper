package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration

/**
 * Dart 局部变量 / 形参类型内联提示收集器。
 *
 * 只处理 [DartVarAccessDeclaration] 与 [DartSimpleFormalParameter]，
 * 类型一律来自 [DartTypeHintsCache]（由 Analysis Server 结果到达后异步填充），
 * 避免在 collect 里同步 hover 导致超时空缺。
 */
@Suppress("UnstableApiUsage")
class DartTypeHintsCollector(
    editor: Editor,
    private val file: PsiFile,
    private val settings: DartTypeInlayHintsProvider.Settings,
) : FactoryInlayHintsCollector(editor) {

    private val submittedOffsets = HashSet<Int>()
    private val cache = DartTypeHintsCache.getInstance(file.project)

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        when (element) {
            is DartVarAccessDeclaration -> {
                if (element.type != null) return true
                submitIfPossible(element.componentName, sink)
            }
            is DartSimpleFormalParameter -> {
                if (element.type != null) return true
                submitIfPossible(element.componentName, sink)
            }
        }
        return true
    }

    private fun submitIfPossible(identifier: DartComponentName, sink: InlayHintsSink) {
        if (!submittedOffsets.add(identifier.textOffset)) return
        val vf = file.virtualFile ?: return
        val type = cache.getType(vf, identifier.textOffset) ?: return
        submitInlayHint(identifier, type, sink)
    }

    private fun submitInlayHint(identifier: DartComponentName, type: String, sink: InlayHintsSink) {
        val identifierRange = identifier.textRange
        val typeRepresentation = factory.smallText(type)
        val (offset, representation) = if (settings.insertBeforeIdentifier) {
            identifierRange.startOffset to factory.seq(
                factory.roundWithBackground(typeRepresentation),
                factory.textSpacePlaceholder(1, true),
            )
        } else {
            identifierRange.endOffset to factory.roundWithBackground(
                factory.seq(factory.smallText(": "), typeRepresentation)
            )
        }
        sink.addInlineElement(offset, true, representation, false)
    }
}
