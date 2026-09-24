# FlutterHelper

[![Version](https://img.shields.io/jetbrains/plugin/v/33267-flutterhelper.svg)](https://plugins.jetbrains.com/plugin/33267-flutterhelper)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33267-flutterhelper.svg)](https://plugins.jetbrains.com/plugin/33267-flutterhelper)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/33267-flutterhelper.svg)](https://plugins.jetbrains.com/plugin/33267-flutterhelper)
[![License](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

**Version 1.0.6** — perf logging.  
**版本 1.0.6** — 性能日志。

Flutter/Dart editor enhancements for **IntelliJ IDEA / Android Studio**.  
面向 **IntelliJ IDEA / Android Studio** 的 Flutter/Dart 编辑增强插件。

**Install / 安装:** [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/33267-flutterhelper)

| | |
|---|---|
| **Requires / 依赖** | Dart, Flutter |
| **UI** | Follows IDE locale (EN default; ZH → `FlutterHelperBundle_zh.properties`)<br>跟随 IDE 语言（默认英文；中文用 `_zh`） |
| **License / 许可证** | [GPL-3.0-or-later](LICENSE) · [NOTICE](NOTICE) |
| **Build / 构建** | JDK **21** |
| **Marketplace** | [33267-flutterhelper](https://plugins.jetbrains.com/plugin/33267-flutterhelper) |

Adapted from [Flutter Enhancement Suite](https://github.com/marius-h/flutter_enhancement_suite) (Marius Höfler, GPL-3.0).  
改编自 [Flutter Enhancement Suite](https://github.com/marius-h/flutter_enhancement_suite)（Marius Höfler，GPL-3.0）。  
Thanks / 致谢: Marius Höfler & Flutter Enhancement Suite.

---

## Perf diagnosis / 性能排查

完整步骤与日志含义见：**[docs/PERF_DIAGNOSIS.md](docs/PERF_DIAGNOSIS.md)**

**Quick / 速查:**

1. **Help → Diagnostic Tools → Debug Log Settings** → add  
   `#com.sixsix.flutter.helper.perf.FlutterHelperPerfLog`
2. Reproduce lag → **Help → Show Log in Finder** → search `FlutterHelperPerf` in `idea.log`
3. Watch `SLOW` (≥200ms) and counters (`gateBusy`, `hovers`, `cvCompute`, …)

若卡顿时几乎没有 `FlutterHelperPerf`，更可能是 Dart Analysis Server / 工程警告，而不是本插件。

---

## Screenshots / 截图

| Type / parameter hints<br>类型 / 参数提示 | New Flutter Widget<br>新建 Flutter 组件 |
|---|---|
| ![type-hints](https://cdn.jsdelivr.net/gh/10470163/FlutterHelper@master/docs/screenshots/type-hints.jpg) | ![new-widget](https://cdn.jsdelivr.net/gh/10470163/FlutterHelper@master/docs/screenshots/new-widget-menu.jpg) |

| Format settings<br>格式化设置 | pubspec → pub.dev |
|---|---|
| ![settings](https://cdn.jsdelivr.net/gh/10470163/FlutterHelper@master/docs/screenshots/settings-format.jpg) | ![pubdev](https://cdn.jsdelivr.net/gh/10470163/FlutterHelper@master/docs/screenshots/pubspec-pubdev.jpg) |

| Extract Widget<br>提取 Widget |
|---|
| ![extract](https://cdn.jsdelivr.net/gh/10470163/FlutterHelper@master/docs/screenshots/extract-widget.jpg) |

Demo project: [examples/screenshot_demo](examples/screenshot_demo/)  
演示工程：[examples/screenshot_demo](examples/screenshot_demo/)

---

## Features / 功能

| Feature / 功能 | How to use / 用法 |
|---|---|
| **Parameter / type hints**<br>参数名 / 类型提示 | Settings → Editor → Inlay Hints → Parameter names & **Dart type hints**<br>设置 → 编辑器 → 内联提示 → 参数名 & **Dart 类型提示** |
| **Code Vision / breadcrumbs**<br>Code Vision / 面包屑 | Inlay Hints → Code Vision; editor bottom bar<br>内联提示 → Code Vision；编辑器底部面包屑 |
| **New Flutter Widget**<br>新建 Flutter 组件 | New → Flutter Widget（Stateless / Stateful / Animated / Inherited） |
| **Extract Widget**<br>提取 Widget | `Ctrl+Alt+E` / `⌘⌥E` → current or new file<br>当前或新文件 |
| **Import / quotes / trailing commas**<br>Import / 引号 / 尾随逗号 | Settings → Tools → **FlutterHelper**（**reformat only** / **仅格式化时**） |
| **Open pub.dev**<br>打开 pub.dev | pubspec versioned package → `Ctrl+B` / `⌘B` |

---

## Install & run / 安装与运行

```bash
./gradlew runIde        # sandbox IDE / 沙箱 IDE
./gradlew buildPlugin   # distribution zip / 打包
./gradlew test          # unit tests / 单元测试
```

1. Run `./gradlew runIde` (or install the plugin zip).  
   执行 `./gradlew runIde`（或安装插件包）。  
2. Open a Flutter project with Dart / Flutter SDK configured.  
   打开已配置 Dart / Flutter SDK 的工程。  
3. Confirm FlutterHelper is enabled under Settings → Plugins.  
   在 设置 → 插件 中确认已启用 FlutterHelper。

---

## Usage notes / 使用说明

### Type & parameter hints / 类型与参数提示

Enable parameter names and **Dart type hints**, then wait for the Analysis Server.  
开启参数名与 **Dart 类型提示**，并等待 Analysis Server。

```dart
void greet(String name, int age) {}

void demo() {
  greet('Ada', 30); // name: / age:
  final bar = 'Hello'; // String
  var n = 1;           // int
}
```

Named arguments already show names — no parameter-name hint.  
已写命名参数时不显示参数名提示。  
Callback parameters use **type** hints. / 回调形参使用**类型**提示。

### New Flutter Widget / 新建 Flutter 组件

Right-click → New → **Flutter Widget**. Name `MyCard` → file `my_card.dart`.  
右键 → 新建 → **Flutter 组件**。名称 `MyCard` → 文件 `my_card.dart`。

Optional templates: project `.flutter_file_templates/*.dart.ft` (listed in Stateless dialog).  
可选模板：项目根 `.flutter_file_templates/*.dart.ft`（出现在无状态对话框）。

### Extract Widget / 提取 Widget

Select a Widget expression → `Ctrl+Alt+E` / `⌘⌥E` → choose name, file, current/new file, `import` / `part of`.  
选中 Widget 表达式 → 快捷键 → 选择名称、文件、位置与引用方式。

### Code style / 代码风格

Settings → Tools → **FlutterHelper**. Applies on **Reformat Code** only (not plain save).  
设置 → 工具 → **FlutterHelper**。仅在**重新格式化**时生效（纯保存不改写）。

- Imports: `package:app/...` or relative (no `./`); current package `lib/` only  
- Quotes: `"..."` → `'...'` (keep raw `r`)  
- Trailing commas: multiline calls / collections; skips `setState`

### pub.dev

On a versioned dependency name in `pubspec.yaml` → Goto Declaration. Skips `sdk:` / `path:` / `git:`.  
在带版本号的依赖包名上 Goto Declaration。不处理 `sdk:` / `path:` / `git:`。

---

## FAQ

| Question / 问题 | Answer / 回答 |
|---|---|
| No type hints? / 无类型提示？ | Enable **Dart type hints**; wait for Analysis Server. |
| No hints on named args? / 命名参数无提示？ | Expected — names are already in source. / 正常。 |
| Save did not rewrite style? / 保存未改风格？ | Those options run on **reformat** only. / 仅格式化时执行。 |

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
