package com.sixsix.flutter.helper.refactoring

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.sixsix.flutter.helper.FlutterHelperBundle
import com.sixsix.flutter.helper.utils.ifLet
import io.flutter.FlutterUtils
import io.flutter.refactoring.ExtractWidgetRefactoring

internal const val NAME_FIELD_WIDTH = 220
internal const val SMALL_PADDING = 4

/**
 * 提取 Flutter Widget：支持留在当前文件或移到新文件。
 * 编辑器右键菜单仅在「Dart 文件 + 有选区」时显示。
 */
class ExtractWidgetToFileAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        event.dataContext.run {
            ifLet(
                getData(CommonDataKeys.PROJECT),
                getData(CommonDataKeys.VIRTUAL_FILE),
                getData(CommonDataKeys.EDITOR),
                getData(CommonDataKeys.CARET),
            ) { (project, file, editor, caret) ->
                createExtractDialog(caret as Caret, project as Project, file as VirtualFile, editor as Editor)
            }
        }
    }

    private fun createExtractDialog(caret: Caret, project: Project, file: VirtualFile, editor: Editor) {
        val offset = caret.selectionStart
        val length = caret.selectionEnd - offset
        if (length <= 0) return

        val refactoring = ExtractWidgetRefactoring(project, file, offset, length)
        val initialStatus = refactoring.checkInitialConditions() ?: return
        if (initialStatus.hasError()) {
            initialStatus.message?.let { message ->
                CommonRefactoringUtil.showErrorHint(project, editor, message, CommonBundle.getErrorTitle(), null)
            }
            return
        }

        ExtractWidgetDialog(project, file, editor, refactoring).show()
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val dart = file != null && FlutterUtils.isDartFile(file)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = dart && hasSelection
        if (e.presentation.isEnabledAndVisible) {
            e.presentation.text = FlutterHelperBundle.message("action.extractWidget")
        }
        super.update(e)
    }
}
