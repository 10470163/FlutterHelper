package com.sixsix.flutter.helper.utils

/**
 * Converts UpperCamelCase / lowerCamelCase to snake_case.
 * 将 UpperCamelCase / lowerCamelCase 转为 snake_case。
 *
 * Examples: `MyWidget` → `my_widget`, `HTTPServer` → `http_server`.
 * 例如：`MyWidget` → `my_widget`，`HTTPServer` → `http_server`。
 *
 * Used for new-widget and extract-widget file names.
 * 用于新建组件文件名、提取组件文件名等场景。
 */
fun String.toSnakeCase(): String {
    if (isEmpty()) return this
    return replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase()
        .trim('_')
}

/**
 * Runs [closure] with unwrapped values when every argument is non-null.
 * 当所有参数均非 null 时执行 [closure]，并将已解包的列表传入。
 *
 * Handy for validating Project / File / Editor / Caret from a DataContext at once.
 * 用于 Action 中一次性校验 Project / File / Editor / Caret 等 DataContext 数据。
 */
inline fun <T : Any> ifLet(vararg elements: T?, closure: (List<T>) -> Unit) {
    if (elements.all { it != null }) {
        @Suppress("UNCHECKED_CAST")
        closure(elements.map { it as T })
    }
}
