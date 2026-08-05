package com.sixsix.flutter.helper.format

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trailing commas: `setState` calls must be skipped.
 * 尾随逗号：setState 调用应被跳过。
 */
class DartTrailingCommaSetStateFilterTest {

    @Test
    fun detectsBareSetState() {
        assertTrue(DartTrailingCommaInjector.isSetStateCallee("setState"))
    }

    @Test
    fun detectsQualifiedSetState() {
        assertTrue(DartTrailingCommaInjector.isSetStateCallee("this.setState"))
        assertTrue(DartTrailingCommaInjector.isSetStateCallee("controller.setState"))
    }

    @Test
    fun ignoresOtherCalls() {
        assertFalse(DartTrailingCommaInjector.isSetStateCallee("setStateIfNeeded"))
        assertFalse(DartTrailingCommaInjector.isSetStateCallee("Column"))
        assertFalse(DartTrailingCommaInjector.isSetStateCallee("Navigator.push"))
        assertFalse(DartTrailingCommaInjector.isSetStateCallee(null))
    }
}
