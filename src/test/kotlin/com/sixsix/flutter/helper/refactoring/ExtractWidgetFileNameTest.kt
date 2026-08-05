package com.sixsix.flutter.helper.refactoring

import com.sixsix.flutter.helper.utils.toSnakeCase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * File-name rules when extracting a widget to a new file.
 * 提取到新文件时的文件名规则。
 */
class ExtractWidgetFileNameTest {

    @Test
    fun widgetNameToSnakeCaseDartFile() {
        assertEquals("home_page_card.dart", "HomePageCard".toSnakeCase() + ".dart")
        assertEquals("new_widget.dart", "NewWidget".toSnakeCase() + ".dart")
    }
}
