package com.sixsix.flutter.helper.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Custom template file filter (only Velocity `.ft` files).
 * 自定义模板文件过滤（仅 Velocity `.ft` 文件）。
 */
class CustomFlutterTemplateManagerTest {

    @Test
    fun acceptsFtFiles() {
        val dir = createTempDirectory("fh-templates").toFile()
        try {
            val ft = File(dir, "my_card.dart.ft").apply { writeText("// template") }
            assertTrue(CustomFlutterTemplateManager.isVelocityTemplateFile(ft))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rejectsDirectoriesAndNonFt() {
        val dir = createTempDirectory("fh-templates").toFile()
        try {
            assertFalse(CustomFlutterTemplateManager.isVelocityTemplateFile(dir))
            val md = File(dir, "README.md").apply { writeText("x") }
            assertFalse(CustomFlutterTemplateManager.isVelocityTemplateFile(md))
        } finally {
            dir.deleteRecursively()
        }
    }
}
