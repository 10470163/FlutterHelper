package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.sixsix.flutter.helper.FlutterHelperBundle
import icons.FlutterIcons

/** Creates an InheritedWidget. / 新建继承组件（InheritedWidget）。 */
class NewInheritedWidgetAction : NewFlutterWidgetActionBase(
    FlutterHelperBundle.message("widget.kind.inherited"),
    FlutterHelperBundle.message("action.newInheritedWidget.description"),
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(FlutterHelperBundle.message("widget.kind.inherited"))
            .addKind(FlutterHelperBundle.message("widget.kind.inherited"), FlutterIcons.Flutter, "inherited_widget")
    }
}
