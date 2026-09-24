package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
 * Visible document range helpers for viewport-first type hints.
 * 可见区文档范围：类型提示优先只解析屏幕附近。
 */
internal object DartTypeHintsViewport {

    /** Extra characters above/below the visible area. / 可见区上下缓冲（字符偏移）。 */
    const val BUFFER_CHARS = 800

    /**
     * Union of visible ranges for [virtualFile] across open editors, expanded by [BUFFER_CHARS].
     * 合并该文件所有打开编辑器的可见区，并加上缓冲。
     * @return null if no text editor is open (caller may skip or use a small default window).
     */
    fun expandedVisibleRange(project: Project, virtualFile: VirtualFile): TextRange? {
        return ReadAction.compute<TextRange?, RuntimeException> {
            if (project.isDisposed) return@compute null
            var start = Int.MAX_VALUE
            var end = Int.MIN_VALUE
            for (fileEditor in FileEditorManager.getInstance(project).getEditors(virtualFile)) {
                val editor = (fileEditor as? TextEditor)?.editor ?: continue
                val range = visibleDocumentRange(editor) ?: continue
                start = minOf(start, range.startOffset)
                end = maxOf(end, range.endOffset)
            }
            if (start == Int.MAX_VALUE) return@compute null
            val docLen = FileEditorManager.getInstance(project).getEditors(virtualFile)
                .mapNotNull { (it as? TextEditor)?.editor?.document?.textLength }
                .maxOrNull() ?: end
            TextRange(
                (start - BUFFER_CHARS).coerceAtLeast(0),
                (end + BUFFER_CHARS).coerceAtMost(docLen),
            )
        }
    }

    fun visibleDocumentRange(editor: Editor): TextRange? {
        val model = editor.scrollingModel
        val area = model.visibleArea
        if (area.width <= 0 || area.height <= 0) return null
        val doc = editor.document
        val logicalStart = editor.xyToLogicalPosition(area.location)
        val logicalEnd = editor.xyToLogicalPosition(
            java.awt.Point(area.x + area.width, area.y + area.height),
        )
        val startOffset = doc.getLineStartOffset(logicalStart.line.coerceIn(0, doc.lineCount - 1))
        val endLine = logicalEnd.line.coerceIn(0, (doc.lineCount - 1).coerceAtLeast(0))
        val endOffset = doc.getLineEndOffset(endLine)
        return TextRange(startOffset, endOffset)
    }
}
