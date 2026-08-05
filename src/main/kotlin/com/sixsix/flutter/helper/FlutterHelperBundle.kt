package com.sixsix.flutter.helper

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/** Bundle path: `messages/FlutterHelperBundle*.properties` (default EN, `zh` Chinese). */
private const val BUNDLE = "messages.FlutterHelperBundle"

/**
 * FlutterHelper i18n entry point.
 * FlutterHelper 国际化入口。
 *
 * Default: English (`FlutterHelperBundle.properties`).
 * 默认英文（`FlutterHelperBundle.properties`）。
 * Chinese: `FlutterHelperBundle_zh.properties` (`zh_CN` falls back to `zh`).
 * 中文：`FlutterHelperBundle_zh.properties`（`zh_CN` 回退到 `zh`）。
 */
internal object FlutterHelperBundle {
    private val instance = DynamicBundle(FlutterHelperBundle::class.java, BUNDLE)

    /**
     * Resolves an i18n message by [key] (EN default; ZH via `_zh` bundle).
     * 按 [key] 解析国际化文案（默认英文，中文走 `_zh` 资源包）。
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): @Nls String {
        return instance.getMessage(key, *params)
    }
}
