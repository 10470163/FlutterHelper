<!-- Keep a Changelog: https://keepachangelog.com -->

# FlutterHelper Changelog

## [1.0.0] — Initial release / 首个正式版本

First public release of FlutterHelper for IntelliJ IDEA / Android Studio.  
FlutterHelper 面向 IntelliJ IDEA / Android Studio 的首个公开发布版本。

### Features / 功能

- **Parameter & type inlay hints** — positional args, locals, lambda parameters  
  **参数名与类型内联提示** — 位置参数、局部变量、lambda 形参
- **Code Vision & breadcrumbs** — usages, inheritors, hierarchy (localized EN/ZH)  
  **Code Vision 与面包屑** — 引用、实现、层级（中英本地化）
- **New Flutter Widget** — Stateless / Stateful / Animated / Inherited  
  **新建 Flutter 组件** — 无状态 / 有状态 / 动画 / 继承
- **Extract Widget** — current or new file (`Ctrl+Alt+E` / `⌘⌥E`)  
  **提取 Widget** — 当前或新文件
- **Reformat pre-steps** — import style, quotes, trailing commas (skips `setState`)  
  **格式化预操作** — import 风格、引号、尾随逗号（忽略 `setState`）
- **pub.dev jump** — Goto Declaration on versioned pubspec packages (`Ctrl+B` / `⌘B`)  
  **打开 pub.dev** — 带版本号依赖包名上 Goto Declaration
- **Bilingual UI** — English default + Chinese (`FlutterHelperBundle` / `_zh`)  
  **中英文界面** — 默认英文 + 中文资源包
- **Docs & demo** — Marketplace screenshots (`docs/screenshots`), sample app (`examples/screenshot_demo`)  
  **文档与演示** — Marketplace 截图、示例工程

### Requirements / 要求

- Plugins: Dart, Flutter  
  依赖插件：Dart、Flutter
- Build toolchain: JDK 21  
  构建：JDK 21

### License / 许可证

- GPL-3.0-or-later — see [LICENSE](LICENSE) and [NOTICE](NOTICE)  
  见 LICENSE、NOTICE
- Portions adapted from [Flutter Enhancement Suite](https://github.com/marius-h/flutter_enhancement_suite) (Marius Höfler, GPL-3.0)  
  部分功能改编自 Flutter Enhancement Suite（Marius Höfler，GPL-3.0）
- Thanks / 致谢: Marius Höfler & Flutter Enhancement Suite
