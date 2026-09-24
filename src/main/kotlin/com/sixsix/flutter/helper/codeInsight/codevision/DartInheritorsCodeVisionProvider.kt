package com.sixsix.flutter.helper.codeInsight.codevision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.actions.DartInheritorsSearcher
import com.jetbrains.lang.dart.psi.DartClassDefinition
import com.jetbrains.lang.dart.psi.DartClassMembers
import com.jetbrains.lang.dart.psi.DartComponent
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.test.DartTestSourcesFilter
import com.jetbrains.lang.dart.util.DartResolveUtil
import com.sixsix.flutter.helper.FlutterHelperBundle
import java.awt.event.MouseEvent

/**
 * Code Vision hint showing implementation counts for abstract Dart types / members.
 * Dart 抽象类 / 抽象成员的实现数 Code Vision。
 *
 * Cached + gated with [DartCodeVisionGate] (shared with usages provider).
 * 与引用 Code Vision 共用闸门，避免并行打爆 Analysis Server。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 */
class DartInheritorsCodeVisionProvider : InheritorsCodeVisionProvider() {
    companion object {
        const val ID = "flutter.helper.dart.inheritors"
        val IMPLEMENTATIONS: Key<Set<DartComponent>> = Key.create("FLUTTER_HELPER_IMPLEMENTATIONS_KEY")
    }

    override val id: String
        get() = ID

    override fun acceptsFile(file: PsiFile): Boolean = file is DartFile

    override fun acceptsElement(element: PsiElement): Boolean {
        if (!element.manager.isInProject(element)) return false
        return (element is DartComponent && element.parent is DartClassMembers && element.isAbstract) ||
                (element is DartClassDefinition && element.isAbstract)
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        if (element !is DartComponent) return null
        if (com.intellij.ide.PowerSaveMode.isEnabled() ||
            com.intellij.openapi.project.DumbService.isDumb(element.project)
        ) {
            return null
        }
        val cache = DartCodeVisionHintCache.getInstance(element.project)
        when (val hit = cache.getIfPresent("inh", element)) {
            is DartCodeVisionHintCache.EntryLookup.Present -> {
                com.sixsix.flutter.helper.perf.FlutterHelperPerfLog.codeVisionCacheHit("inh")
                return hit.hint
            }
            DartCodeVisionHintCache.EntryLookup.Absent -> Unit
        }
        return DartCodeVisionGate.tryRun {
            val start = System.nanoTime()
            val hint = computeHint(element, file)
            com.sixsix.flutter.helper.perf.FlutterHelperPerfLog.codeVisionComputed(
                "inh",
                (System.nanoTime() - start) / 1_000_000L,
            )
            cache.put("inh", element, hint)
            hint
        }
    }

    private fun computeHint(element: DartComponent, file: PsiFile): String? {
        val anchor = element.componentName ?: return null
        val project = element.project
        val das = DartAnalysisServerService.getInstance(project)
        val items = das.search_getTypeHierarchy(file.virtualFile, anchor.textRange.startOffset, false)
        if (items.isEmpty()) return null

        val implementations = when (element) {
            is DartClassDefinition -> DartInheritorsSearcher.getSubClasses(
                project, GlobalSearchScope.allScope(project), items
            )
            else -> DartInheritorsSearcher.getSubMembers(
                project, GlobalSearchScope.allScope(project), items
            )
        }
        element.putUserData(IMPLEMENTATIONS, implementations)

        val sourceImplementationsLabel = when (val count = implementations.size) {
            0 -> FlutterHelperBundle.message("codeVision.implementations.none")
            1 -> FlutterHelperBundle.message("codeVision.implementations.one")
            else -> FlutterHelperBundle.message("codeVision.implementations.many", count)
        }

        val testCount = implementations.count {
            DartTestSourcesFilter.isTestSources(it.containingFile.virtualFile, it.project)
        }
        if (testCount == 0) return sourceImplementationsLabel
        val testLabel = FlutterHelperBundle.message("codeVision.usages.inTests", testCount)
        return "$sourceImplementationsLabel ($testLabel)"
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        if (event == null || element !is DartComponent) return
        val anchor = element.componentName ?: return
        val components = element.getUserData(IMPLEMENTATIONS) ?: return

        if (element is DartClassDefinition) {
            val popupTitle = DaemonBundle.message("navigation.title.subclass", anchor.name, components.size, "")
            val findUsagesTitle = DartBundle.message("tab.title.subclasses.of.0", anchor.name)
            openImplementationDialog(event, components, popupTitle, findUsagesTitle)
        } else {
            val popupTitle = DaemonBundle.message("navigation.title.overrider.method", anchor.name, components.size)
            val findUsagesTitle = DartBundle.message("tab.title.overriding.methods.of.0", anchor.name)
            openImplementationDialog(event, components, popupTitle, findUsagesTitle)
        }
    }

    private fun openImplementationDialog(
        event: MouseEvent,
        components: Set<DartComponent>,
        popupTitle: String,
        findUsagesTitle: String,
    ) {
        PsiElementListNavigator.openTargets(
            event,
            DartResolveUtil.getComponentNameArray(components),
            popupTitle,
            findUsagesTitle,
            DefaultPsiElementCellRenderer(),
        )
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter(DartReferencesCodeVisionProvider.ID))
}
