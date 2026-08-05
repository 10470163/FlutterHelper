package com.sixsix.flutter.helper.utils

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.lang.dart.psi.DartClassDefinition
import com.jetbrains.lang.dart.psi.DartClassMembers
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartEnumDefinition
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.psi.DartVarDeclarationList
import com.jetbrains.lang.dart.util.DartElementGenerator

/**
 * Creates an `import '...'` PSI node via a dummy Dart file for insertion into a real file.
 * 通过 Dummy Dart 文件生成一条 `import '...'` PSI 节点，用于插入到真实文件。
 */
fun Project.createImportStatement(libraryName: String): PsiElement {
    return DartElementGenerator.createDummyFile(this, "import '$libraryName';").firstChild
}

/**
 * Collects leading Dart import statements at the top of the file.
 * 提取文件顶部连续的 Dart import 语句。
 *
 * Stops at the first non-whitespace, non-comment, non-import node.
 * 遇非空白、非注释、非 import 节点即停止。
 *
 * Used when extracting a widget to a new file to copy dependency imports.
 * 用于「提取组件到新文件」时把依赖 import 复制到新文件。
 */
fun PsiFile.extractDartImportStatements(): List<PsiElement> {
    val importStatements = mutableListOf<PsiElement>()
    for (child in children) {
        when (child) {
            is DartImportStatement -> importStatements.add(child)
            is PsiWhiteSpace, is PsiComment -> continue
            else -> break
        }
    }
    return importStatements
}

/**
 * Whether this PSI element should show Code Vision (usages / inheritors / VCS authors).
 * 判断该 PSI 元素是否应显示 Code Vision（引用次数 / 实现数 / VCS 贡献者）。
 *
 * Limited to in-project classes, enums, top-level functions, and class/enum members.
 * 仅限项目内：类、枚举、顶层函数，以及类/枚举成员中的组件与变量声明列表。
 */
fun PsiElement.enablesCodeVision(): Boolean {
    if (!manager.isInProject(this)) return false
    return ((this is DartComponent || this is DartVarDeclarationList) &&
            (parent is DartClassMembers || parent is DartEnumDefinition)) ||
            this is DartClassDefinition ||
            this is DartFunctionDeclarationWithBodyOrNative ||
            this is DartEnumDefinition
}
