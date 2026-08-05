package com.sixsix.flutter.helper.format

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression
import com.jetbrains.lang.dart.util.DartElementGenerator

/**
 * Dart string-quote normalizer.
 * Dart 字符串引号规范化。
 *
 * Rules / 规则：
 * - Plain `"..."` → `'...'` (skip if content has unescaped `'`)
 *   普通 `"..."` → `'...'`（内容含未转义 `'` 则跳过）
 * - Raw: `r"..."` → `r'...'` (keep raw; dropping `r` would change `\n` meaning)
 *   raw：`r"..."` → `r'...'`（保留 raw，仅改引号）
 * - Triple-quoted docs are left unchanged / 三引号文档字符串不处理
 */
object DartQuoteNormalizer {

    /**
     * Walks all string literals and replaces from the end so offsets stay valid.
     * 遍历文件中所有字符串字面量，从后往前替换以保证偏移稳定。
     */
    fun normalize(file: PsiFile) {
        val targets = mutableListOf<DartStringLiteralExpression>()
        file.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is DartStringLiteralExpression) {
                    targets.add(element)
                }
                super.visitElement(element)
            }
        })

        for (literal in targets.asReversed()) {
            if (!literal.isValid) continue
            val text = literal.text
            val converted = convertToSingleQuotes(text) ?: continue
            if (converted == text) continue
            val replacement = DartElementGenerator
                .createDummyFile(file.project, "var __q = $converted;")
                .children
                .asSequence()
                .mapNotNull { findStringLiteral(it) }
                .firstOrNull()
                ?: continue
            literal.replace(replacement)
        }
    }

    private fun findStringLiteral(element: PsiElement): DartStringLiteralExpression? {
        if (element is DartStringLiteralExpression) return element
        for (child in element.children) {
            findStringLiteral(child)?.let { return it }
        }
        return null
    }

    /**
     * - `"hello"` → `'hello'`
     * - `r"C:\path"` → `r'C:\path'` (keep raw / 保留 raw)
     * - Content with `'` or triple quotes → no conversion (null)
     *   内容含 `'`、三引号 → 不转换（返回 null）
     */
    internal fun convertToSingleQuotes(text: String): String? {
        if (text.length < 2) return null
        if (text.startsWith("\"\"\"") || text.startsWith("'''") ||
            text.startsWith("r\"\"\"") || text.startsWith("r'''")
        ) {
            return null
        }

        val isRaw = text.startsWith("r\"") || text.startsWith("r'")
        if (isRaw) {
            if (!text.startsWith("r\"") || !text.endsWith("\"")) return null
            val content = text.substring(2, text.length - 1)
            if (containsUnescapedSingleQuote(content)) return null
            return "r'$content'"
        }

        if (!text.startsWith("\"") || !text.endsWith("\"")) return null
        val content = text.substring(1, text.length - 1)
        if (containsUnescapedSingleQuote(content)) return null
        return "'$content'"
    }

    private fun containsUnescapedSingleQuote(content: String): Boolean {
        var i = 0
        while (i < content.length) {
            val c = content[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '\'') return true
            i++
        }
        return false
    }
}
