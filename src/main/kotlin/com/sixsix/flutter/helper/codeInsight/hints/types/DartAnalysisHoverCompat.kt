package com.sixsix.flutter.helper.codeInsight.hints.types

import com.google.dart.server.GetHoverConsumer
import com.google.dart.server.generated.AnalysisServer
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import org.dartlang.analysis.server.protocol.HoverInformation
import org.dartlang.analysis.server.protocol.RequestError
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Compatible hover lookup across Dart plugin versions.
 * 跨 Dart 插件版本的 hover 查询兼容层。
 *
 * Important for performance: never hold a ReadAction while waiting for the Analysis Server.
 * 性能要点：等待 Analysis Server 时绝不持有 ReadAction（否则会拖死整机读锁）。
 *
 * Dart ≤508 exposes DAS.analysis_getHover; Dart 509+ removed that wrapper.
 * Never call DAS.analysis_getHover directly (Plugin Verifier).
 */
internal object DartAnalysisHoverCompat {

    private val LOG = Logger.getInstance(DartAnalysisHoverCompat::class.java)

    private val dasHoverMethod: Method? = try {
        DartAnalysisServerService::class.java.getMethod(
            "analysis_getHover",
            VirtualFile::class.java,
            Int::class.javaPrimitiveType,
        )
    } catch (_: NoSuchMethodException) {
        null
    }

    private val serverField: Field? = try {
        DartAnalysisServerService::class.java.getDeclaredField("myServer").apply {
            isAccessible = true
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Must be called **outside** a long-held ReadAction.
     * 必须在长时间 ReadAction **之外**调用。
     */
    @Suppress("UNCHECKED_CAST")
    fun getHovers(das: DartAnalysisServerService, virtualFile: VirtualFile, offset: Int): List<HoverInformation> {
        dasHoverMethod?.let { method ->
            return try {
                (method.invoke(das, virtualFile, offset) as? List<HoverInformation>).orEmpty()
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                LOG.debug("DAS analysis_getHover via reflection failed", e)
                emptyList()
            }
        }
        return getHoversViaAnalysisServer(das, virtualFile, offset)
    }

    private fun getHoversViaAnalysisServer(
        das: DartAnalysisServerService,
        virtualFile: VirtualFile,
        offset: Int,
    ): List<HoverInformation> {
        val (server, fileUri, originalOffset) = try {
            ReadAction.compute<Triple<AnalysisServer, String, Int>?, RuntimeException> {
                val s = serverField?.get(das) as? AnalysisServer ?: return@compute null
                val uri = das.getFileUri(virtualFile)
                val off = try {
                    das.getOriginalOffset(virtualFile, offset)
                } catch (_: Exception) {
                    offset
                }
                Triple(s, uri, off)
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("Cannot prepare AnalysisServer hover", e)
            null
        } ?: return emptyList()

        val result = mutableListOf<HoverInformation>()
        val latch = CountDownLatch(1)
        try {
            // Blocking wait is intentionally outside ReadAction.
            // 阻塞等待刻意放在 ReadAction 之外。
            server.analysis_getHover(fileUri, originalOffset, object : GetHoverConsumer {
                override fun computedHovers(hovers: Array<out HoverInformation>?) {
                    if (hovers != null) {
                        result.addAll(hovers)
                    }
                    latch.countDown()
                }

                override fun onError(error: RequestError) {
                    latch.countDown()
                }
            })
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            LOG.debug("AnalysisServer.analysis_getHover failed", e)
            return emptyList()
        }

        try {
            latch.await(HOVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    /** Keep short: many sequential hovers must not freeze the machine. / 保持短超时，避免连续 hover 拖垮机器。 */
    private const val HOVER_TIMEOUT_MS = 60L
}
