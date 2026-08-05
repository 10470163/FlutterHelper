package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.psi.DartFile
import com.sixsix.flutter.helper.FlutterHelperBundle
import javax.swing.JComponent

/**
 * Settings persistence key for Dart type inlays (top-level: avoid CompanionObjectInExtension).
 * 类型提示设置持久化 key（顶层声明，避免扩展点 companion 警告）。
 */
private val DART_TYPE_HINTS_SETTINGS_KEY =
    SettingsKey<DartTypeInlayHintsProvider.Settings>("flutter.helper.dart.type.hints")

/**
 * Inlay provider for Dart local / parameter types.
 * Dart 变量/形参类型内联提示 Provider。
 *
 * Settings: Editor → Inlay Hints → Types → Dart type hints.
 * 设置路径：设置 | 编辑器 | 内联提示 | 类型 | Dart 类型提示。
 *
 * Enable the toggle; inferred types appear after Analysis Server is ready.
 * 请确认该开关已勾选；Analysis Server 启动后才会显示推断类型。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
@Suppress("UnstableApiUsage")
class DartTypeInlayHintsProvider : InlayHintsProvider<DartTypeInlayHintsProvider.Settings> {

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink,
    ): InlayHintsCollector? {
        if (file !is DartFile) return null
        // Ensure refresh service is running; schedule a fill when cache misses.
        // 确保刷新服务已启动；缓存未命中时调度一次填充。
        DartTypeHintsRefreshService.getInstance(file.project).ensureCached(file.virtualFile)
        return DartTypeHintsCollector(editor, file, settings)
    }

    override fun createSettings() = Settings()

    data class Settings(
        /** true: type before identifier; false: `name: Type`. / true：类型在标识符前；false：显示为 `name: Type`。 */
        var insertBeforeIdentifier: Boolean = true,
    )

    override val name: String
        get() = FlutterHelperBundle.message("hints.type.name")

    override val key: SettingsKey<Settings> = DART_TYPE_HINTS_SETTINGS_KEY

    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP

    override val previewText = """
        void foo() {
          final bar = "Hello there, General Kenobi!";
          ValueListenableBuilder(
            valueListenable: listenable,
            builder: (context, value, child) {
              return Text(value);
            },
          );
        }
    """.trimIndent()

    override fun isLanguageSupported(language: Language): Boolean =
        language.isKindOf(DartLanguage.INSTANCE)

    override fun createConfigurable(settings: Settings): ImmediateConfigurable = object : ImmediateConfigurable {
        private val initial = settings.insertBeforeIdentifier

        override fun createComponent(listener: ChangeListener): JComponent = panel {
            row {
                checkBox(FlutterHelperBundle.message("hints.type.beforeIdentifier")).applyToComponent {
                    isSelected = settings.insertBeforeIdentifier
                    addItemListener {
                        settings.insertBeforeIdentifier = isSelected
                        listener.settingsChanged()
                    }
                }
            }
        }

        override fun reset() {
            settings.insertBeforeIdentifier = initial
            super.reset()
        }

        override val mainCheckboxText: String
            get() = FlutterHelperBundle.message("hints.type.mainCheckbox")
    }
}
