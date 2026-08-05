package com.sixsix.flutter.helper.codeInsight.hints.types

import org.dartlang.analysis.server.protocol.HoverInformation

/**
 * Parses a displayable type string from Analysis Server hover data.
 * 从 Analysis Server hover 结果解析可用类型字符串。
 */
internal object DartTypeHintParser {

    /**
     * Extracts a type from [hover], preferring static / propagated types, then description text.
     * 从 [hover] 提取类型：优先 static / propagated，再回退描述文本。
     */
    fun typeFromHover(hover: HoverInformation, name: String?): String? {
        usefulType(hover.staticType)?.let { return it }
        usefulType(hover.propagatedType)?.let { return it }
        if (name.isNullOrBlank()) return null
        parseTypeFromDescription(hover.elementDescription, name)?.let { return it }
        parseTypeFromDescription(hover.parameter, name)?.let { return it }
        return null
    }

    private fun usefulType(type: String?): String? {
        val t = type?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (t == "dynamic" || t == "InvalidType" || t == "void") return null
        return t
    }

    /**
     * Extracts a type from hover description text, e.g.:
     * 从 hover 描述中提取类型，例如：
     * - `BuildContext context`
     * - `(BuildContext context)`
     * - `final String bar`
     */
    fun parseTypeFromDescription(description: String?, name: String): String? {
        if (description.isNullOrBlank() || name.isBlank()) return null
        val cleaned = description.trim()
            .removePrefix("(").removeSuffix(")")
            .trim()
            .substringBefore("=")
            .trim()

        val idx = cleaned.lastIndexOf(name)
        if (idx <= 0) return null

        val after = idx + name.length
        if (after < cleaned.length) {
            val ch = cleaned[after]
            if (ch.isLetterOrDigit() || ch == '_') return null
        }
        val before = cleaned[idx - 1]
        if (!before.isWhitespace() && before != '(' && before != ',') return null

        var typePart = cleaned.substring(0, idx).trim()
        // 整段形参列表时只取最后一个逗号之后的类型
        if (',' in typePart) {
            typePart = typePart.substringAfterLast(',').trim()
        }
        typePart = typePart
            .removePrefix("final ").removePrefix("const ").removePrefix("var ")
            .trim()
        return usefulType(typePart)?.takeIf {
            it.first().isLetter() || it.first() == '_'
        }
    }
}
