import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Marketplace plugins required for Dart PSI / Flutter refactoring APIs
        // Marketplace 插件：Dart PSI / Flutter 重构 API
        plugin("Dart", "509.0.0")
        plugin("io.flutter", "94.0.0")

        // pubspec.yaml → pub.dev jump needs YAML PSI
        // pubspec.yaml → pub.dev 跳转需要 YAML PSI
        bundledPlugin("org.jetbrains.plugins.yaml")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
        }
    }

    // IDEA 2025.3 TraverseUIStarter requires LocalizationUtil default locale;
    // Chinese OS still fails even with -Duser.language=en (platform limit, not our code).
    // IDEA 2025.3 TraverseUIStarter 要求 LocalizationUtil 为默认 locale；
    // 中文系统即使用 -Duser.language=en / LANG=en_US 仍会失败（平台限制，非业务代码问题）。
    // Settings → Tools → FlutterHelper still works; only global Settings search skips indexing our options.
    // 设置页「工具 → FlutterHelper」仍完整可用，仅 Settings 全局搜索不索引本插件选项。
    buildSearchableOptions = false
}
