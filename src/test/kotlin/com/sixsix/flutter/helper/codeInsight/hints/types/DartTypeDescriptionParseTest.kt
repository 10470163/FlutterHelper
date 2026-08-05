package com.sixsix.flutter.helper.codeInsight.hints.types

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 类型描述解析单元测试（不依赖 Analysis Server）。
 */
class DartTypeDescriptionParseTest {

    @Test
    fun parsesBuildContext() {
        assertEquals("BuildContext", DartTypeHintParser.parseTypeFromDescription("BuildContext context", "context"))
    }

    @Test
    fun parsesNullableWidget() {
        assertEquals("Widget?", DartTypeHintParser.parseTypeFromDescription("(Widget? child)", "child"))
    }

    @Test
    fun parsesGeneric() {
        assertEquals("List<String>", DartTypeHintParser.parseTypeFromDescription("List<String> value", "value"))
    }

    @Test
    fun parsesFinalString() {
        assertEquals("String", DartTypeHintParser.parseTypeFromDescription("final String bar", "bar"))
    }

    @Test
    fun parsesShortNameInt() {
        assertEquals("int", DartTypeHintParser.parseTypeFromDescription("int n", "n"))
    }

    @Test
    fun parsesFromParamList() {
        assertEquals(
            "String",
            DartTypeHintParser.parseTypeFromDescription(
                "BuildContext context, String value, Widget? child",
                "value",
            ),
        )
    }

    @Test
    fun skipsDynamic() {
        assertNull(DartTypeHintParser.parseTypeFromDescription("dynamic value", "value"))
    }
}
