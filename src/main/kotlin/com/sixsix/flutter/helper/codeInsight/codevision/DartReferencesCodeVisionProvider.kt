package com.sixsix.flutter.helper.codeInsight.codevision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartVarDeclarationList
import com.jetbrains.lang.dart.test.DartTestSourcesFilter
import com.sixsix.flutter.helper.FlutterHelperBundle
import com.sixsix.flutter.helper.utils.enablesCodeVision
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Code Vision hint showing usage counts for Dart declarations (e.g. `3 usages`).
 * Dart 声明的引用次数 Code Vision（如 `3 usages`）。
 *
 * Counts project usages via [DartServerFindUsagesHandler] and separates test-directory hits.
 * 通过 [DartServerFindUsagesHandler] 统计项目内引用，并区分测试目录中的次数。
 * Click opens Find Usages.
 * 点击后跳转到查找用法界面。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
class DartReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {
    companion object {
        const val ID = "flutter.helper.dart.references"
        private const val MAX_USAGES = 100
    }

    override val id: String
        get() = ID

    override fun acceptsFile(file: PsiFile): Boolean = file is DartFile

    override fun acceptsElement(element: PsiElement): Boolean = element.enablesCodeVision()

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val el = if (element is DartVarDeclarationList) element.varAccessDeclaration else element as? DartComponent
            ?: return null
        val referencedElement = el.componentName ?: return null

        val scope = GlobalSearchScope.projectScope(element.project)
        val usages = AtomicInteger()
        val testUsages = AtomicInteger()
        val finder = DartServerFindUsagesHandler(element)
        val options = FindUsagesOptions(scope)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        finder.processElementUsages(referencedElement, {
            it.element?.let { usageElement ->
                if (DartTestSourcesFilter.isTestSources(usageElement.containingFile.virtualFile, usageElement.project)) {
                    testUsages.incrementAndGet()
                }
            }
            usages.incrementAndGet() <= MAX_USAGES
        }, options)

        val sourceUsagesLabel = when (val count = usages.get()) {
            0 -> if (!el.isAbstract) FlutterHelperBundle.message("codeVision.usages.none") else return null
            1 -> FlutterHelperBundle.message("codeVision.usages.one")
            else -> FlutterHelperBundle.message("codeVision.usages.many", count)
        }

        val testCount = testUsages.get()
        if (testCount == 0) return sourceUsagesLabel
        val testUsagesLabel = FlutterHelperBundle.message("codeVision.usages.inTests", testCount)
        return "$sourceUsagesLabel ($testUsagesLabel)"
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val actualElement = if (element is DartVarDeclarationList) element.varAccessDeclaration else element
        GotoDeclarationAction.startFindUsages(
            editor,
            element.project,
            actualElement,
            if (event == null) null else RelativePoint(event),
        )
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()
}
