package com.sixsix.flutter.helper.codeInsight.hints.types

import com.google.dart.server.AnalysisServerListenerAdapter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.psi.DartFile
import org.dartlang.analysis.server.protocol.HighlightRegion
import org.dartlang.analysis.server.protocol.Outline
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

/**
 * Analysis Server 对某文件产出 highlights/outline 后，先异步重建类型缓存，再刷新 inlay。
 *
 * 避免在 Daemon collect 阶段同步连打 hover（易超时导致提示随机空缺）。
 */
@Service(Service.Level.PROJECT)
class DartTypeHintsRefreshService(private val project: Project) : Disposable {

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pendingPaths = LinkedHashSet<String>()
    private val inflight = ConcurrentHashMap<String, Future<*>>()

    private val listener = object : AnalysisServerListenerAdapter() {
        override fun computedHighlights(file: String, highlights: MutableList<HighlightRegion>?) {
            scheduleRefresh(file)
        }

        override fun computedOutline(file: String, outline: Outline?) {
            scheduleRefresh(file)
        }

        override fun flushedResults(files: MutableList<String>?) {
            files ?: return
            val cache = DartTypeHintsCache.getInstance(project)
            for (file in files) {
                val vf = ReadAction.compute<VirtualFile?, RuntimeException> {
                    getDartFileInfo(project, file).findFile()
                } ?: continue
                cache.invalidate(vf)
            }
        }
    }

    init {
        val das = DartAnalysisServerService.getInstance(project)
        das.addAnalysisServerListener(listener)
        Disposer.register(this) {
            das.removeAnalysisServerListener(listener)
        }
    }

    private fun scheduleRefresh(analysisFile: String) {
        if (project.isDisposed) return
        synchronized(pendingPaths) {
            pendingPaths.add(analysisFile)
        }
        alarm.cancelAllRequests()
        alarm.addRequest({ flushPending() }, DEBOUNCE_MS)
    }

    private fun flushPending() {
        if (project.isDisposed) return
        val files = synchronized(pendingPaths) {
            pendingPaths.toList().also { pendingPaths.clear() }
        }
        if (files.isEmpty()) return

        val openFiles = ApplicationManager.getApplication().runReadAction<Set<VirtualFile>> {
            if (project.isDisposed) emptySet()
            else FileEditorManager.getInstance(project).openFiles.toSet()
        }

        for (path in files) {
            val vf = ReadAction.compute<VirtualFile?, RuntimeException> {
                getDartFileInfo(project, path).findFile()
            } ?: continue
            if (vf !in openFiles) continue
            rebuildAndRefresh(vf)
        }
    }

    /**
     * 打开文件时若缓存缺失，主动调度一次重建（不等待下一次 highlights）。
     */
    fun ensureCached(virtualFile: VirtualFile?) {
        if (virtualFile == null || project.isDisposed) return
        val cache = DartTypeHintsCache.getInstance(project)
        if (cache.hasFile(virtualFile)) return
        rebuildAndRefresh(virtualFile)
    }

    private fun rebuildAndRefresh(virtualFile: VirtualFile) {
        val key = virtualFile.url
        inflight.remove(key)?.cancel(true)

        val future = AppExecutorUtil.getAppExecutorService().submit {
            if (project.isDisposed) return@submit
            val ready = ReadAction.compute<Boolean, RuntimeException> {
                !project.isDisposed &&
                    DartAnalysisServerService.getInstance(project).serverReadyForRequest()
            }
            if (!ready) return@submit

            try {
                DartTypeHintsCache.getInstance(project).rebuild(virtualFile)
            } catch (_: Exception) {
                // ignore; still refresh so collector can read whatever cache exists
            }

            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed) return@invokeLater
                refreshInlays(virtualFile)
            }, ModalityState.defaultModalityState())
        }
        inflight[key] = future
    }

    /**
     * 刷新该文件的 inlay。
     * 不使用 [com.intellij.codeInsight.hints.InlayHintsFactory]：该类已在 IDEA 2026.2 删除（IJPL-242229），
     * 打包安装到新 IDE 会 NoClassDefFoundError；[DaemonCodeAnalyzer.restart] 即可触发 inlay 重算。
     */
    @Suppress("UnstableApiUsage")
    private fun refreshInlays(virtualFile: VirtualFile) {
        val psi = ReadAction.compute<DartFile?, RuntimeException> {
            PsiManager.getInstance(project).findFile(virtualFile) as? DartFile
        } ?: return
        DaemonCodeAnalyzer.getInstance(project).restart(psi, REFRESH_REASON)
    }

    override fun dispose() {
        alarm.cancelAllRequests()
        inflight.values.forEach { it.cancel(true) }
        inflight.clear()
    }

    companion object {
        private const val DEBOUNCE_MS = 350
        private val REFRESH_REASON = Any()

        fun getInstance(project: Project): DartTypeHintsRefreshService =
            project.getService(DartTypeHintsRefreshService::class.java)
    }
}
