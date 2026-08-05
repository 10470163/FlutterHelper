package com.sixsix.flutter.helper.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * snake_case conversion used for widget / extract file names.
 * 组件与提取文件名使用的 snake_case 转换。
 */
class CaseUtilsTest {

    @Test
    fun upperCamelToSnake() {
        assertEquals("my_widget", "MyWidget".toSnakeCase())
        assertEquals("home_page_card", "HomePageCard".toSnakeCase())
    }

    @Test
    fun acronymsSplitBeforeLowercaseRun() {
        assertEquals("http_server", "HTTPServer".toSnakeCase())
        assertEquals("xml_http_request", "XMLHttpRequest".toSnakeCase())
    }

    @Test
    fun alreadySnakeStays() {
        assertEquals("my_widget", "my_widget".toSnakeCase())
    }

    @Test
    fun emptyRemainsEmpty() {
        assertEquals("", "".toSnakeCase())
    }
}
