package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.util.DartElementGenerator
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import com.intellij.psi.util.PsiTreeUtil

/**
 * Verifies PSI targets for type hints: var declarations and lambda params are collectible.
 * 验证类型提示目标 PSI 节点：变量声明与 lambda 形参均为可收集类型。
 */
class DartTypeHintsPsiTest : BasePlatformTestCase() {

    fun testVarAccessAndLambdaParamsAreSimpleFormalOrVarAccess() {
        val text = """
            void foo() {
              var n = 1;
              final bar = 'x';
              builder(context, value, child) {
                return n;
              }
            }
            void builder(a, b, c) {}
        """.trimIndent()

        val file = DartElementGenerator.createDummyFile(project, text)
        val vars = PsiTreeUtil.findChildrenOfType(file, DartVarAccessDeclaration::class.java)
        assertTrue("expected var/final declarations", vars.size >= 2)
        assertTrue(vars.any { it.componentName.name == "n" && it.type == null })
        assertTrue(vars.any { it.componentName.name == "bar" && it.type == null })

        val params = PsiTreeUtil.findChildrenOfType(file, DartSimpleFormalParameter::class.java)
            .filter { it.type == null }
        val names = params.map { it.componentName.name }.toSet()
        assertTrue(
            "lambda/function params should be SimpleFormalParameter: $names",
            names.containsAll(setOf("context", "value", "child")),
        )
    }
}
