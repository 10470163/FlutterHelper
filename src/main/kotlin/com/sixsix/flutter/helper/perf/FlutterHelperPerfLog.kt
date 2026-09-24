package com.sixsix.flutter.helper.perf

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance diagnostics for real-project lag investigation.
 * 真实项目卡顿排查用的性能诊断日志。
 *
 * Enable in IDE:
 * Help → Diagnostic Tools → Debug Log Settings → add:
 * `#com.sixsix.flutter.helper.perf.FlutterHelperPerfLog`
 *
 * Then reproduce lag and inspect `idea.log` (Help → Show Log in Finder).
 * 开启后复现卡顿，在 idea.log 中搜索 `FlutterHelperPerf`。
 *
 * Always logs at INFO when a single operation exceeds [SLOW_MS] (visible without debug).
 * 单次操作超过 [SLOW_MS] 时即使未开 Debug 也会 INFO 打出，便于发现尖刺。
 */
object FlutterHelperPerfLog {
    private val LOG = Logger.getInstance(FlutterHelperPerfLog::class.java)

    /** Slow threshold for always-on INFO lines. / 始终输出 INFO 的慢操作阈值。 */
    const val SLOW_MS = 200L

    private val fillCount = AtomicLong()
    private val hoverCount = AtomicLong()
    private val hoverSkipInterval = AtomicLong()
    private val gateBusy = AtomicLong()
    private val viewportSkip = AtomicLong()
    private val codeVisionCompute = AtomicLong()
    private val codeVisionCacheHit = AtomicLong()
    private val daemonRestart = AtomicLong()

    fun debug(event: String, details: String = "") {
        if (!LOG.isDebugEnabled) return
        if (details.isEmpty()) {
            LOG.debug("FlutterHelperPerf | $event")
        } else {
            LOG.debug("FlutterHelperPerf | $event | $details")
        }
    }

    fun timed(event: String, details: String = "", block: () -> Unit) {
        val start = System.nanoTime()
        try {
            block()
        } finally {
            val ms = (System.nanoTime() - start) / 1_000_000L
            recordDuration(event, ms, details)
        }
    }

    fun <T> timedValue(event: String, details: String = "", block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val ms = (System.nanoTime() - start) / 1_000_000L
            recordDuration(event, ms, details)
        }
    }

    fun recordDuration(event: String, durationMs: Long, details: String = "") {
        val suffix = if (details.isEmpty()) "" else " | $details"
        if (durationMs >= SLOW_MS) {
            LOG.info("FlutterHelperPerf | SLOW $event | ${durationMs}ms$suffix | ${snapshot()}")
        } else {
            debug(event, "${durationMs}ms$suffix")
        }
    }

    fun fillStarted(fileName: String) {
        fillCount.incrementAndGet()
        debug("typeHints.fill.start", fileName)
    }

    fun fillDone(fileName: String, durationMs: Long, targets: Int, resolved: Int, changed: Boolean) {
        recordDuration(
            "typeHints.fill",
            durationMs,
            "file=$fileName targets=$targets resolved=$resolved changed=$changed",
        )
    }

    fun hoverOk() {
        hoverCount.incrementAndGet()
    }

    fun hoverSkippedInterval() {
        hoverSkipInterval.incrementAndGet()
        debug("hover.skip.interval")
    }

    fun gateBusy(who: String) {
        gateBusy.incrementAndGet()
        debug("das.gate.busy", who)
    }

    fun viewportSatisfied(fileName: String) {
        viewportSkip.incrementAndGet()
        debug("typeHints.viewport.satisfied", fileName)
    }

    fun codeVisionCacheHit(kind: String) {
        codeVisionCacheHit.incrementAndGet()
        debug("codeVision.cache.hit", kind)
    }

    fun codeVisionComputed(kind: String, durationMs: Long) {
        codeVisionCompute.incrementAndGet()
        recordDuration("codeVision.compute", durationMs, "kind=$kind")
    }

    fun daemonRestart(fileName: String) {
        daemonRestart.incrementAndGet()
        debug("daemon.restart", fileName)
    }

    /** Compact counters for slow lines. / 慢日志附带计数摘要。 */
    fun snapshot(): String =
        "fills=${fillCount.get()} hovers=${hoverCount.get()} hoverSkip=${hoverSkipInterval.get()} " +
            "gateBusy=${gateBusy.get()} viewportSkip=${viewportSkip.get()} " +
            "cvCompute=${codeVisionCompute.get()} cvCache=${codeVisionCacheHit.get()} " +
            "daemonRestart=${daemonRestart.get()}"
}
