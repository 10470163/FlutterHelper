package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.sixsix.flutter.helper.FlutterHelperBundle
import icons.FlutterIcons

/** Creates a StatefulWidget. / 新建有状态组件（StatefulWidget）。 */
class NewStatefulWidgetAction : NewFlutterWidgetActionBase(
    FlutterHelperBundle.message("widget.kind.stateful"),
    FlutterHelperBundle.message("action.newStatefulWidget.description"),
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(FlutterHelperBundle.message("widget.kind.stateful"))
            .addKind(FlutterHelperBundle.message("widget.kind.stateful"), FlutterIcons.Flutter, "stateful_widget")
    }
}
