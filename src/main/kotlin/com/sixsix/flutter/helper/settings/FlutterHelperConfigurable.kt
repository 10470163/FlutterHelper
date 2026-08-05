package com.sixsix.flutter.helper.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.sixsix.flutter.helper.FlutterHelperBundle

/**
 * FlutterHelper 设置页（设置 | 工具 | FlutterHelper）。
 *
 * 使用 IntelliJ UI DSL 双向绑定 [FlutterHelperSettings] 字段，
 * 用户点击 Apply/OK 后由 [BoundConfigurable] 自动提交。
 */
class FlutterHelperConfigurable : BoundConfigurable(FlutterHelperBundle.message("settings.displayName")) {

    private val settings = FlutterHelperSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        group(FlutterHelperBundle.message("settings.displayName")) {
            row {
                checkBox(FlutterHelperBundle.message("settings.enableImportRewrite"))
                    .bindSelected(settings::enableImportRewrite)
            }
            buttonsGroup(FlutterHelperBundle.message("settings.importStyle")) {
                row {
                    radioButton(
                        FlutterHelperBundle.message("settings.importStyle.package"),
                        ImportStyle.PACKAGE
                    )
                }
                row {
                    radioButton(
                        FlutterHelperBundle.message("settings.importStyle.relative"),
                        ImportStyle.RELATIVE
                    )
                }
            }.bind(settings::importStyle)
            row {
                checkBox(FlutterHelperBundle.message("settings.enableQuoteNormalize"))
                    .bindSelected(settings::enableQuoteNormalize)
            }
            row {
                checkBox(FlutterHelperBundle.message("settings.enableTrailingCommas"))
                    .bindSelected(settings::enableTrailingCommas)
            }
        }
    }
}
