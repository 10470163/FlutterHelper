package com.sixsix.flutter.helper.editor

import com.intellij.lang.Language
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringDescriptionLocation
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.DartClassDefinition
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartFactoryConstructorDeclaration
import com.jetbrains.lang.dart.psi.DartFunctionDeclarationWithBodyOrNative
import com.jetbrains.lang.dart.psi.DartGetterDeclaration
import com.jetbrains.lang.dart.psi.DartMethodDeclaration
import com.jetbrains.lang.dart.psi.DartSetterDeclaration
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import icons.FlutterIcons
import javax.swing.Icon

/**
 * Dart breadcrumbs: classes, methods, top-level functions, getters/setters, fields.
 * Dart 面包屑：类、方法、顶层函数、getter/setter、字段。
 *
 * Method labels prefer [DartComponent.getName], falling back to [DartComponent.getComponentName].
 * 方法名优先取 [DartComponent.getName]，为空时回退 [DartComponent.getComponentName]。
 */
class DartBreadcrumbsInfoProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(DartLanguage.INSTANCE)

    override fun acceptElement(e: PsiElement): Boolean = when (e) {
        is DartClassDefinition,
        is DartFactoryConstructorDeclaration,
        is DartMethodDeclaration,
        is DartFunctionDeclarationWithBodyOrNative,
        is DartGetterDeclaration,
        is DartSetterDeclaration,
        is DartVarAccessDeclaration,
        -> componentDisplayName(e) != null
        else -> false
    }

    override fun getElementInfo(e: PsiElement): String = when (e) {
        is DartClassDefinition -> componentDisplayName(e) ?: ""
        is DartFactoryConstructorDeclaration -> "factory ${componentDisplayName(e) ?: ""}()"
        is DartMethodDeclaration,
        is DartFunctionDeclarationWithBodyOrNative,
        -> "${componentDisplayName(e) ?: ""}()"
        is DartGetterDeclaration -> "get ${componentDisplayName(e) ?: ""}"
        is DartSetterDeclaration -> "set ${componentDisplayName(e) ?: ""}"
        is DartVarAccessDeclaration -> componentDisplayName(e) ?: ""
        else -> ""
    }

    override fun getParent(element: PsiElement): PsiElement? {
        // Skip intermediate nodes (e.g. ClassMembers) and find a displayable ancestor.
        // 跳过 ClassMembers 等中间节点，直接找到可展示的祖先（类 / 外层方法）。
        var parent = element.parent
        while (parent != null) {
            if (acceptElement(parent)) return parent
            parent = parent.parent
        }
        return null
    }

    override fun getElementIcon(element: PsiElement): Icon? {
        if (element is DartClassDefinition) {
            return if (element.isAbstract) FlutterIcons.CustomClassAbstract else FlutterIcons.CustomClass
        }
        return super.getElementIcon(element)
    }

    override fun getElementTooltip(element: PsiElement): String =
        ElementDescriptionUtil.getElementDescription(element, RefactoringDescriptionLocation.WITH_PARENT)

    private fun componentDisplayName(element: PsiElement): String? {
        if (element is DartComponent) {
            return element.name?.takeIf { it.isNotBlank() }
                ?: element.componentName?.name?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
