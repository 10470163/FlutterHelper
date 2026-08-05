package com.sixsix.flutter.helper.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Relative import path rules (never use `./`) and package URI helpers.
 * 相对 import 路径规则（永不使用 `./`）与 package URI 辅助方法。
 */
class DartImportRelativePathTest {

    @Test
    fun sameDirectoryHasNoDotSlash() {
        assertEquals(
            "other_widget.dart",
            DartImportStyleProcessor.relativePath("/proj/lib/ui", "/proj/lib/ui/other_widget.dart"),
        )
    }

    @Test
    fun nestedDirectoryHasNoDotSlash() {
        assertEquals(
            "data/providers.dart",
            DartImportStyleProcessor.relativePath("/proj/lib", "/proj/lib/data/providers.dart"),
        )
    }

    @Test
    fun parentDirectoryUsesDotDot() {
        assertEquals(
            "../widgets/card.dart",
            DartImportStyleProcessor.relativePath("/proj/lib/ui", "/proj/lib/widgets/card.dart"),
        )
    }

    @Test
    fun packageImportUriJoinsLibRelativePath() {
        assertEquals(
            "package:my_app/widgets/card.dart",
            DartImportStyleProcessor.packageImportUri("my_app", "widgets/card.dart"),
        )
    }

    @Test
    fun packageImportUriTrimsLeadingSlash() {
        assertEquals(
            "package:demo/home.dart",
            DartImportStyleProcessor.packageImportUri("demo", "/home.dart"),
        )
    }
}
