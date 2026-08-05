package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.sixsix.flutter.helper.FlutterHelperBundle
import icons.FlutterIcons

/**
 * Creates a StatefulWidget with AnimationController.
 * 新建带动画控制器的有状态组件。
 */
class NewAnimatedWidgetAction : NewFlutterWidgetActionBase(
    FlutterHelperBundle.message("widget.kind.animated"),
    FlutterHelperBundle.message("action.newAnimatedWidget.description"),
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(FlutterHelperBundle.message("widget.kind.animated"))
            .addKind(FlutterHelperBundle.message("widget.kind.animated"), FlutterIcons.Flutter, "animated_widget")
    }
}
