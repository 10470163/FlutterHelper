package com.sixsix.flutter.helper.codeInsight.hints.types

import com.google.dart.server.GetHoverConsumer
import com.google.dart.server.generated.AnalysisServer
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
 * Dart ≤508 exposes [DartAnalysisServerService.analysis_getHover]; Dart 509+ removed that
 * wrapper (Plugin Verifier `NoSuchMethodError`), while the underlying
 * [AnalysisServer.analysis_getHover] protocol call still exists.
 * Dart ≤508 提供 [DartAnalysisServerService.analysis_getHover]；509+ 已移除该包装方法
 * （Plugin Verifier 报 NoSuchMethodError），但底层 AnalysisServer 协议调用仍可用。
 *
 * Never call `analysis_getHover` on DAS directly — keep bytecode free of that invokevirtual.
 * 切勿直接调用 DAS 上的 `analysis_getHover`，避免字节码出现该 invokevirtual。
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
        val server = try {
            serverField?.get(das) as? AnalysisServer
        } catch (e: Exception) {
            LOG.debug("Cannot access DartAnalysisServerService.myServer", e)
            null
        } ?: return emptyList()

        val fileUri = try {
            das.getFileUri(virtualFile)
        } catch (e: Exception) {
            LOG.debug("getFileUri failed", e)
            return emptyList()
        }

        val originalOffset = try {
            das.getOriginalOffset(virtualFile, offset)
        } catch (_: Exception) {
            offset
        }

        val result = mutableListOf<HoverInformation>()
        val latch = CountDownLatch(1)
        try {
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

    private const val HOVER_TIMEOUT_MS = 1_000L
}
