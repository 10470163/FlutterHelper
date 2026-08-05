package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.sixsix.flutter.helper.FlutterHelperBundle
import icons.FlutterIcons

/** Creates a StatelessWidget. / 新建无状态组件（StatelessWidget）。 */
class NewStatelessWidgetAction : NewFlutterWidgetActionBase(
    FlutterHelperBundle.message("widget.kind.stateless"),
    FlutterHelperBundle.message("action.newStatelessWidget.description"),
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(FlutterHelperBundle.message("widget.kind.stateless"))
            .addKind(FlutterHelperBundle.message("widget.kind.stateless"), FlutterIcons.Flutter, "stateless_widget")
        customTemplatesManager.appendCustomFileTemplatesTo(builder, project)
    }
}
