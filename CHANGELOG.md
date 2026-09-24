<!-- Keep a Changelog: https://keepachangelog.com -->

# FlutterHelper Changelog

## [1.0.6] — 2026-09-24

### Changed / 变更

- **No performance toggles** — best-effort path is always on (viewport-first type hints; no highlight-storm refresh; shared DAS single-flight).  
  **无性能开关** — 始终走最佳路径（可见区类型提示；不跟 highlights 风暴；DAS 单飞）。
- **Perf logging** — enable `#com.sixsix.flutter.helper.perf.FlutterHelperPerfLog` in Debug Log Settings; operations ≥200ms always log `FlutterHelperPerf | SLOW ...` to `idea.log`. See [docs/PERF_DIAGNOSIS.md](docs/PERF_DIAGNOSIS.md).  
  **性能日志** — Debug Log Settings 加入上述类别；≥200ms 打出 `SLOW`；排查说明见文档。

## [1.0.5] — 2026-09-24

### Fixed / 修复

- **Slow Dart analyzes / many warnings** — do not refresh type hints on every analysis highlight; shared DAS single-flight with Code Vision; hover spacing + tighter budgets; `ensureCached` debounced.  
  **分析慢 / 警告多** — 不再因每次 highlights 刷类型提示；与 Code Vision 共用 DAS 单飞；hover 限流。

## [1.0.4] — 2026-09-24

### Fixed / 修复

- **More editor lag reductions** — PSI walk limited to viewport subtree; skip fill when viewport already satisfied; coalesce worker jobs; hover 80ms / ≤16 targets / 200ms budget; highlight debounce 1.2s; Power Save / Dumb mode skips heavy work; parameter hints skip all-named calls; Code Vision respects Power Save.  
  **进一步降低卡顿** — PSI 只扫可见子树；可见区已满足则跳过；任务去重；更紧预算；省电/Dumb 模式跳过重活；参数提示跳过全命名调用。

## [1.0.3] — 2026-09-24

### Fixed / 修复

- **Editor / system lag** — type hints are **viewport-first** (visible range + buffer; scroll fills more); hover outside ReadAction (150ms); ≤24 targets / 320ms per fill; single-thread worker; highlight debounce 900ms; daemon restart only on cache change; Code Vision cached + single-flight gate; usage scan cap 40.  
  **编辑器 / 整机卡顿** — 类型提示**可见区优先**（可见范围+缓冲，滚动再补算）；hover 不占读锁；单次填充限额；单线程队列；Code Vision 缓存+串行闸门。

## [1.0.2] — 2026-09-20

### Fixed / 修复

- **Type hints × Dart 509+** — stop calling removed `DartAnalysisServerService.analysis_getHover` (Plugin Verifier `NoSuchMethodError` on IDEA 2025.3+). Resolve hover via reflective DAS method when present, otherwise `AnalysisServer.analysis_getHover`.  
  **类型提示 × Dart 509+** — 不再直接调用已移除的 `analysis_getHover`（Marketplace 兼容性检查失败）。优先反射调用旧 API，否则走 AnalysisServer 协议接口。

## [1.0.1] — 2026-09-20

### Fixed / 修复

- **Parameter hints crash** — avoid `DartNewExpression.getReferenceExpressionList()`, which is missing in some Dart plugin builds (`NoSuchMethodError`). Resolve constructor references via PSI children instead.  
  **参数名提示崩溃** — 不再调用部分 Dart 插件版本已移除的 `getReferenceExpressionList()`，改为通过 PSI 子节点解析构造引用。

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
