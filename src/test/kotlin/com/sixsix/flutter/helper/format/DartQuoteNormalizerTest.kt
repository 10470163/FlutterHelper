package com.sixsix.flutter.helper.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DartQuoteNormalizerTest {
    @Test
    fun convertsSimpleDoubleQuotes() {
        assertEquals("'hello'", DartQuoteNormalizer.convertToSingleQuotes("\"hello\""))
    }

    @Test
    fun skipsWhenContainsSingleQuote() {
        assertNull(DartQuoteNormalizer.convertToSingleQuotes("\"say 'hi'\""))
    }

    @Test
    fun convertsRawKeepingPrefix() {
        // 保留 r，仅改引号；去掉 r 会改变反斜杠语义
        assertEquals("r'C:\\path'", DartQuoteNormalizer.convertToSingleQuotes("r\"C:\\path\""))
    }

    @Test
    fun skipsTripleQuotes() {
        assertNull(DartQuoteNormalizer.convertToSingleQuotes("\"\"\"doc\"\"\""))
    }
}
