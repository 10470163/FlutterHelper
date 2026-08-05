package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.module.ModuleTypeWithWebFeatures
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FileTypeIndex
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.sdk.DartSdk
import com.sixsix.flutter.helper.utils.toSnakeCase
import icons.FlutterIcons

/**
 * Base action for “New Flutter Widget”.
 * 「新建 Flutter 组件」动作基类。
 *
 * Handles snake_case file names, Dart SDK availability, and custom templates.
 * 负责：snake_case 文件名、Dart/SDK 可用性判断、自定义模板创建。
 *
 * Shared by Stateless / Stateful / Animated / Inherited menu items.
 * 四个二级菜单项（无状态 / 有状态 / 动画 / 继承）共用本基类。
 */
abstract class NewFlutterWidgetActionBase(
    private val actionDisplayName: String,
    description: String,
    protected val customTemplatesManager: CustomFlutterTemplateManager = CustomFlutterTemplateManager(),
) : CreateFileFromTemplateAction(actionDisplayName, description, FlutterIcons.Flutter) {

    override fun createFile(name: String, templateName: String, dir: PsiDirectory): PsiFile? {
        if (customTemplatesManager.isCustomFileTemplate(templateName)) {
            val template = customTemplatesManager.createCustomFileTemplate(templateName, dir.project)
            return template?.let { createFileFromTemplate(name, it, dir) }
                ?: super.createFile(name, templateName, dir)
        }
        return super.createFile(name, templateName, dir)
    }

    /**
     * Converts the widget name to a snake_case file name (`MyCard` → `my_card.dart`).
     * 将组件名转为 snake_case 文件名。
     */
    override fun createFileFromTemplate(name: String?, template: FileTemplate?, dir: PsiDirectory?): PsiFile {
        return super.createFileFromTemplate(name?.toSnakeCase(), template, dir)
    }

    /**
     * When a module is present: it already contains `.dart` files, or Dart SDK is configured
     * and the module supports WebFeatures.
     * 有模块时：模块内已有 `.dart`，或项目配置了 Dart SDK 且模块支持 WebFeatures。
     */
    override fun isAvailable(dataContext: DataContext): Boolean {
        val module = PlatformCoreDataKeys.MODULE.getData(dataContext) ?: return super.isAvailable(dataContext)
        if (!super.isAvailable(dataContext)) return false
        val hasDartSdk = DartSdk.getDartSdk(module.project) != null && ModuleTypeWithWebFeatures.isAvailable(module)
        return FileTypeIndex.containsFileOfType(DartFileType.INSTANCE, module.moduleContentScope) || hasDartSdk
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        actionDisplayName
}
