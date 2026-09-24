package com.sixsix.flutter.helper.analysis

import com.sixsix.flutter.helper.perf.FlutterHelperPerfLog
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-flight guard for Dart Analysis Server RPC (hover / Find Usages / hierarchy).
 * Dart Analysis Server RPC 单飞闸门（hover / Find Usages / 类型层级）。
 */
internal object DartAnalysisLoadGuard {
    private val permit = Semaphore(1, true)
    private val lastHoverNanos = AtomicLong(0)

    fun <T> tryRun(block: () -> T): T? {
        if (!permit.tryAcquire(0, TimeUnit.MILLISECONDS)) {
            FlutterHelperPerfLog.gateBusy("tryRun")
            return null
        }
        return try {
            block()
        } finally {
            permit.release()
        }
    }

    fun tryAcquireHoverSlot(minIntervalMs: Long = 50L): Boolean {
        val now = System.nanoTime()
        while (true) {
            val prev = lastHoverNanos.get()
            if (now - prev < TimeUnit.MILLISECONDS.toNanos(minIntervalMs)) return false
            if (lastHoverNanos.compareAndSet(prev, now)) return true
        }
    }
}
