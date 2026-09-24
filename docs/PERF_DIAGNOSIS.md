# FlutterHelper 性能日志排查指南

Performance log diagnosis guide (EN + 中文).

适用版本 / Applies to: **1.0.6+**

---

## 1. 何时使用 / When to use

编辑器卡顿、整机打字也顿，或怀疑 FlutterHelper 加重了 Dart Analysis Server 负担时，用本指南。

Use this when the editor lags, the whole machine feels slow while typing, or you suspect FlutterHelper is adding load on top of a slow Dart Analysis Server.

---

## 2. 开启日志 / Enable logging

### 2.1 打开 Debug Log Settings

1. 菜单：**Help → Diagnostic Tools → Debug Log Settings…**  
   （部分中文界面：帮助 → 诊断工具 → 调试日志设置）
2. 在文本框中**新增一行**（不要删掉已有内容）：

```text
#com.sixsix.flutter.helper.perf.FlutterHelperPerfLog
```

3. 确定保存。无需重启 IDE（若无日志，可重启一次再试）。

### 2.2 说明

| 级别 | 内容 |
|------|------|
| **DEBUG**（需上面配置） | 调度、跳过、缓存命中、gate busy、hover 限流等明细 |
| **INFO / SLOW**（默认也会写） | 单次操作 ≥ **200ms** 时自动打出，关键字含 `SLOW` |

未开启 Debug 时，仍可能看到 `FlutterHelperPerf | SLOW ...`；要看完整过程请务必加上上面那一行。

---

## 3. 复现卡顿 / Reproduce

1. 打开真实 Flutter/Dart 工程中**较大、警告较多**的文件。
2. 正常操作 1～2 分钟：打字、滚动、切换标签、触发 Code Vision（看类/方法上方 usages）。
3. 记下大概时间点，便于对照日志时间戳。

---

## 4. 打开日志文件 / Open idea.log

1. **Help → Show Log in Finder**（Windows: **Show Log in Explorer**；Linux: **Show Log in Files**）
2. 打开当前目录下的 **`idea.log`**（必要时还有 `idea.log.1` 等轮转文件）
3. 用编辑器搜索：

```text
FlutterHelperPerf
```

---

## 5. 日志怎么读 / How to read

### 5.1 行格式示例

```text
FlutterHelperPerf | SLOW typeHints.fill | 320ms | file=home_page.dart targets=12 resolved=8 changed=true | fills=3 hovers=40 ...
FlutterHelperPerf | typeHints.schedule | home_page.dart reason=scroll
FlutterHelperPerf | das.gate.busy | tryRun
FlutterHelperPerf | codeVision.compute | 450ms | kind=refs
```

### 5.2 关键事件

| 事件 / Event | 含义 | 若频繁/很慢说明 |
|--------------|------|-----------------|
| `typeHints.schedule` | 计划填充类型提示（打开冷缓存 / 滚动） | 正常；若每秒很多次 → 滚动/打开过于频繁触发 |
| `typeHints.fill` / `SLOW typeHints.fill` | 可见区 hover 填类型缓存 | ≥200ms 多 → 类型提示链路重，DAS hover 慢 |
| `typeHints.viewport.satisfied` | 可见区已算过，跳过 | 正常，说明限流生效 |
| `typeHints.queue.dedupe` | 同文件任务去重 | 正常 |
| `typeHints.skip.serverNotReady` | Analysis Server 未就绪 | DAS 还在忙/启动中 |
| `hover.skip.interval` | hover 最小间隔限流 | 正常；过高说明本想打很多 hover |
| `das.gate.busy` | DAS 单飞闸门忙（类型提示与 Code Vision 互斥） | 插件在排队，避免并发打爆 DAS |
| `codeVision.cache.hit` | Code Vision 命中缓存 | 正常 |
| `codeVision.compute` / `SLOW ... refs/inh` | 算 usages / implementations | ≥200ms 多 → Find Usages / 类型层级贵 |
| `daemon.restart` | 刷新 inlay 触发 daemon restart | 过密可能导致高亮抖动 |

### 5.3 行尾计数器（snapshot）

慢日志末尾类似：

`fills=… hovers=… hoverSkip=… gateBusy=… viewportSkip=… cvCompute=… cvCache=… daemonRestart=…`

| 字段 | 含义 |
|------|------|
| `fills` | 类型提示填充次数 |
| `hovers` | 成功发起的 hover 次数 |
| `hoverSkip` | 被间隔限流跳过的 hover |
| `gateBusy` | 因 DAS 单飞忙而跳过的次数 |
| `viewportSkip` | 可见区已满足而跳过 |
| `cvCompute` / `cvCache` | Code Vision 实算 / 缓存命中 |
| `daemonRestart` | daemon 重启次数 |

会话内累计，重启 IDE 会清零。

---

## 6. 结论怎么判 / Decision guide

### A. 卡顿时段 **几乎没有** `FlutterHelperPerf`

→ 更可能是 **Dart Analysis Server / 工程本身**（警告多、生成代码未 exclude、工程过大），而不是 FlutterHelper。

建议：

- 检查 `analysis_options.yaml` 是否 exclude `*.g.dart` / `*.freezed.dart` / `build/**`
- 活动监视器看 `dart` / Analysis Server CPU
- 临时禁用 FlutterHelper 对照

### B. 大量 `SLOW typeHints.fill` + `hovers` 很高

→ 类型提示在向 DAS 要 hover，DAS 慢时会拖编辑器。

把含 `SLOW typeHints.fill` 的几行日志发开发者；可再压预算或改策略。

### C. 大量 `SLOW codeVision.compute`（`kind=refs` / `inh`）

→ Code Vision 的 Find Usages / 类型层级贵。

可对照：Settings → Editor → Inlay Hints → Code Vision 临时关掉 Dart usages/implementations。

### D. `gateBusy` 很高，但很少 `SLOW`

→ 插件已在排队保护 DAS；卡顿仍可能来自 DAS 本身或其他插件。

### E. `daemonRestart` 异常密集

→ inlay 刷新过勤；把时间段日志发开发者。

---

## 7. 反馈时请附带 / What to send

请提供：

1. IDE 与 FlutterHelper 版本  
2. 卡顿时的操作（打字 / 滚动 / 打开大文件等）  
3. `idea.log` 中含 `FlutterHelperPerf` 的片段（前后各约 50 行即可）  
4. （可选）临时禁用 FlutterHelper 后是否明显好转  

---

## 8. 关闭调试日志 / Turn off

Debug Log Settings 中删除：

```text
#com.sixsix.flutter.helper.perf.FlutterHelperPerfLog
```

以免长期 DEBUG 撑大日志。`SLOW` INFO 仍可能偶发，属正常。

---

## 9. 相关代码 / Related code

- `com.sixsix.flutter.helper.perf.FlutterHelperPerfLog`
- 类型提示：`DartTypeHintsRefreshService` / `DartTypeHintsCache`
- Code Vision：`DartReferencesCodeVisionProvider` / `DartInheritorsCodeVisionProvider`
- DAS 闸门：`DartAnalysisLoadGuard`
