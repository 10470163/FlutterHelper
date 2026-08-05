package com.sixsix.flutter.helper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Target style for in-project Dart imports.
 * 项目内 import 的目标书写风格。
 *
 * - [PACKAGE]: `package:<app>/<path>.dart` (typical Flutter recommendation)
 *   写成 `package:应用名/路径.dart`（Flutter 常规推荐）
 * - [RELATIVE]: path relative to the current file, e.g. `../widgets/card.dart`
 *   写成相对当前文件的路径
 */
enum class ImportStyle {
    PACKAGE,
    RELATIVE,
}

/**
 * Application-level persistent settings for FlutterHelper.
 * FlutterHelper 应用级持久化设置。
 *
 * Stored in `FlutterHelperSettings.xml` (IDE config directory).
 * 存储位置：`FlutterHelperSettings.xml`（IDE 配置目录）。
 *
 * Edited under Settings → Tools → FlutterHelper; affects reformat pre-steps.
 * 在设置页「工具 | FlutterHelper」中修改，影响格式化（Reformat Code）前的代码风格预操作。
 */
@Service(Service.Level.APP)
@State(name = "FlutterHelperSettings", storages = [Storage("FlutterHelperSettings.xml")])
class FlutterHelperSettings : PersistentStateComponent<FlutterHelperSettings> {

    /**
     * Target import style for in-project rewrites (default Package).
     * 项目内 import 重写目标风格，默认 Package。
     */
    var importStyle: ImportStyle = ImportStyle.PACKAGE

    /**
     * Whether to rewrite in-project imports before reformat.
     * 是否在格式化前重写项目内 import。
     */
    var enableImportRewrite: Boolean = true

    /**
     * Whether to convert safe double-quoted strings to single quotes before reformat.
     * 是否在格式化前将安全的双引号字符串转为单引号。
     */
    var enableQuoteNormalize: Boolean = true

    /**
     * Whether to add trailing commas to multiline structures before reformat.
     * 是否在格式化前为多行结构补尾随逗号。
     */
    var enableTrailingCommas: Boolean = true

    override fun getState(): FlutterHelperSettings = this

    override fun loadState(state: FlutterHelperSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /** Returns the singleton settings instance. / 获取全局唯一设置实例。 */
        fun getInstance(): FlutterHelperSettings =
            ApplicationManager.getApplication().getService(FlutterHelperSettings::class.java)
    }
}
