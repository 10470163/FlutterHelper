package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 项目启动后立刻创建 [DartTypeHintsRefreshService]，注册 Analysis Server 监听。
 */
class DartTypeHintsRefreshStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        DartTypeHintsRefreshService.getInstance(project)
    }
}
