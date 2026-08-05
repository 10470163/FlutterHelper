package com.sixsix.flutter.helper.codeInsight.codevision

import com.intellij.codeInsight.hints.VcsCodeVisionLanguageContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.sixsix.flutter.helper.utils.enablesCodeVision
import java.awt.event.MouseEvent

/**
 * 为 Dart 声明启用 VCS Code Vision（代码贡献者 / Git 历史作者信息）。
 *
 * 接受条件与引用 Code Vision 一致，见 [enablesCodeVision]。
 *
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
@Suppress("UnstableApiUsage")
class DartVcsCodeVisionContext : VcsCodeVisionLanguageContext {
    override fun handleClick(mouseEvent: MouseEvent, editor: Editor, element: PsiElement) {}

    override fun isAccepted(element: PsiElement): Boolean = element.enablesCodeVision()
}
