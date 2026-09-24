package com.sixsix.flutter.helper.codeInsight.codevision

import com.sixsix.flutter.helper.analysis.DartAnalysisLoadGuard

/**
 * Serializes expensive Code Vision Analysis Server work.
 * 串行化昂贵的 Code Vision / Analysis Server 工作。
 */
internal object DartCodeVisionGate {
    fun <T> tryRun(block: () -> T): T? = DartAnalysisLoadGuard.tryRun(block)
}
