package com.sixsix.flutter.helper.codeInsight.hints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.DartArguments
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartNamedArgument
import com.jetbrains.lang.dart.psi.DartNewExpression
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import com.jetbrains.lang.dart.util.DartResolveUtil

/**
 * Parameter-name inlay hints for Dart method / constructor calls.
 * Dart 方法调用参数名内联提示。
 *
 * Shows the formal parameter name to the left of positional arguments
 * (e.g. `greet(/*name:*/ 'Ada', /*age:*/ 30)`).
 * 对位置参数（非命名参数）在实参左侧显示形参名。
 *
 * Skips named arguments and cases where the argument text already matches the parameter name.
 * 已命名参数、实参文本与形参名相同时跳过。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
@Suppress("UnstableApiUsage")
class DartInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getDefaultBlackList(): MutableSet<String> {
        return mutableSetOf("dart.core", "(fn)", "(a)", "(a, b)")
    }

    override fun getBlackListDependencyLanguage(): Language = DartLanguage.INSTANCE

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val arguments: DartArguments = when (element) {
            is DartCallExpression -> element.childrenOfType<DartArguments>().firstOrNull()
            is DartNewExpression -> element.arguments
            else -> null
        } ?: return emptyList()

        val expressionList = arguments.argumentList?.expressionList ?: return emptyList()
        // Fast path: only named args → nothing to show.
        // 快路径：全是命名参数则无需提示。
        if (expressionList.none { it !is DartNamedArgument }) return emptyList()

        val functionDescription = getFunctionDescription(element)
        val parameterNames = functionDescription?.parameters?.map { it.text } ?: return emptyList()

        var positionalIndex = 0
        return expressionList.mapNotNull { expression ->
            if (expression is DartNamedArgument) {
                return@mapNotNull null
            }
            if (positionalIndex >= parameterNames.size) return@mapNotNull null
            val parameterName = parameterNames[positionalIndex].parameterName()
            positionalIndex++
            if (parameterName.isBlank() || parameterName == expression.text) return@mapNotNull null
            InlayInfo(parameterName, expression.textOffset)
        }
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        return getFunctionDescription(element)?.let { getMethodInfo(it) }
    }

    private fun getMethodInfo(functionDescription: DartFunctionDescription): HintInfo.MethodInfo {
        val parameterNames = functionDescription.parameters.map { it.text.parameterName() }
        return HintInfo.MethodInfo(functionDescription.name, parameterNames)
    }

    private fun getFunctionDescription(element: PsiElement): DartFunctionDescription? = when (element) {
        is DartCallExpression -> DartFunctionDescription.tryGetDescription(element)
        is DartNewExpression -> {
            val type = element.type
            val classResolveResult = DartResolveUtil.resolveClassByType(type)
            val referenceExpressions = element.childrenOfType<DartReferenceExpression>()
            val psiElement = referenceExpressions.lastOrNull() ?: type?.referenceExpression
            val target = psiElement?.resolve()
            if (target is DartComponentName) {
                val component = target.parent as? DartComponent ?: return null
                DartFunctionDescription.createDescription(component, classResolveResult)
            } else null
        }
        else -> null
    }

    private fun String.parameterName(): String {
        if (!contains('(')) {
            return NON_IDENT.replace(this, " ").trim().split(WHITESPACE).lastOrNull() ?: this
        }
        return split("(").first()
            .let { NON_IDENT_KEEP_SPACE.replace(it, "") }
            .trim()
            .split(WHITESPACE)
            .lastOrNull() ?: this
    }

    companion object {
        private val NON_IDENT = Regex("[^a-zA-Z0-9_]")
        private val NON_IDENT_KEEP_SPACE = Regex("[^a-zA-Z0-9_\\s]")
        private val WHITESPACE = Regex("\\s+")
    }
}
