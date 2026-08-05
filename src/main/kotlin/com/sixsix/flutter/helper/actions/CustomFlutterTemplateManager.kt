package com.sixsix.flutter.helper.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import icons.FlutterIcons
import java.io.File

/**
 * Relative path under the project root for custom Flutter file templates.
 * 项目根下自定义模板目录。
 */
private const val CUSTOM_TEMPLATES_PATH = ".flutter_file_templates/"

/**
 * Internal name prefix for custom templates in the new-file dialog.
 * 自定义模板内部名称前缀。
 */
private const val CUSTOM_FILE_TEMPLATE_NAME_PREFIX = "@custom:"

/**
 * Velocity file-template suffix.
 * Velocity 模板后缀。
 */
private const val VELOCITY_FILE_TEMPLATE_SUFFIX = ".ft"

/**
 * Manages custom Flutter widget file templates.
 * 自定义 Flutter 组件模板管理器。
 *
 * Scans `.flutter_file_templates` for Velocity `.ft` files and lists them
 * in the Stateless New Widget dialog.
 * 扫描项目 `.flutter_file_templates` 下的 `.ft` 文件，挂到无状态组件新建对话框。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
class CustomFlutterTemplateManager {

    /**
     * Whether [templateName] is a custom template (`@custom:` prefix).
     * 是否为自定义模板。
     */
    fun isCustomFileTemplate(templateName: String): Boolean =
        templateName.startsWith(CUSTOM_FILE_TEMPLATE_NAME_PREFIX)

    /**
     * Appends project custom templates to the new-file dialog kind list.
     * 追加自定义模板到新建对话框。
     */
    fun appendCustomFileTemplatesTo(builder: CreateFileFromTemplateDialog.Builder, project: Project) {
        findCustomTemplates(project).forEach {
            val customTemplateName = it.name.substringBefore('.')
            val kind = customTemplateName.snakeCaseToTemplateName()
            builder.addKind(kind, FlutterIcons.Flutter, CUSTOM_FILE_TEMPLATE_NAME_PREFIX + customTemplateName)
        }
    }

    /**
     * Loads a custom template from disk by dialog name, or null if missing.
     * 按对话框名称读取磁盘模板；找不到时返回 null。
     */
    fun createCustomFileTemplate(templateName: String, project: Project): FileTemplate? {
        val templateFilename = templateName.removePrefix(CUSTOM_FILE_TEMPLATE_NAME_PREFIX)
        val templateFile = findCustomTemplates(project)
            .firstOrNull { it.name.substringBefore('.') == templateFilename }
            ?: return null

        return CustomFileTemplate(
            templateFilename,
            computeExtensionFromFileTemplate(templateFile.name),
        ).apply { text = templateFile.readText() }
    }

    private fun findCustomTemplates(project: Project): Array<File> {
        val templatesDir = computeCustomTemplatesDirectory(project)
        return templatesDir?.listFiles { file -> isVelocityTemplateFile(file) } ?: arrayOf()
    }

    private fun computeCustomTemplatesDirectory(project: Project): File? {
        return project.guessProjectDir()
            ?.toNioPath()
            ?.resolve(CUSTOM_TEMPLATES_PATH)
            ?.toFile()
    }

    /**
     * Parses extension `dart` from `name.dart.ft`.
     * 从 `name.dart.ft` 解析扩展名。
     */
    private fun computeExtensionFromFileTemplate(filename: String): String =
        filename.removeSuffix(VELOCITY_FILE_TEMPLATE_SUFFIX).substringAfter('.')

    /**
     * `my_custom_widget` to `My Custom Widget` for dialog display.
     * 对话框展示名。
     */
    private fun String.snakeCaseToTemplateName(): String =
        split('_').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    companion object {
        /**
         * Whether [file] is a Velocity `.ft` template file (not a directory).
         * 判断 [file] 是否为 Velocity `.ft` 模板文件（排除目录）。
         */
        internal fun isVelocityTemplateFile(file: File): Boolean =
            file.isFile && file.name.endsWith(VELOCITY_FILE_TEMPLATE_SUFFIX)
    }
}
