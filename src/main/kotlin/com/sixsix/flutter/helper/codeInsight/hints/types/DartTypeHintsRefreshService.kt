package com.sixsix.flutter.helper.codeInsight.hints.types

import com.google.dart.server.AnalysisServerListenerAdapter
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.analyzer.getDartFileInfo
import com.jetbrains.lang.dart.psi.DartFile
import com.sixsix.flutter.helper.analysis.DartAnalysisLoadGuard
import com.sixsix.flutter.helper.perf.FlutterHelperPerfLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Viewport-first type-hint refresh (open/scroll only — never on highlight storms).
 * 可见区优先类型提示刷新（仅打开/滚动；不在 highlights 风暴时触发）。
 */
@Service(Service.Level.PROJECT)
class DartTypeHintsRefreshService(private val project: Project) : Disposable {

    private val fillAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pendingFillUrls = LinkedHashSet<String>()
    private val queuedUrls = ConcurrentHashMap.newKeySet<String>()
    private val inflight = ConcurrentHashMap<String, Future<*>>()
    private val disposed = AtomicBoolean(false)

    private val worker = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "FlutterHelper-TypeHints",
        1,
    )

    private val visibleAreaListener = VisibleAreaListener { event: VisibleAreaEvent ->
        if (project.isDisposed || disposed.get() || PowerSaveMode.isEnabled()) return@VisibleAreaListener
        val old = event.oldRectangle
        val neu = event.newRectangle
        if (old != null && kotlin.math.abs(old.y - neu.y) < 64 && old.height == neu.height) {
            return@VisibleAreaListener
        }
        val vf = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return@VisibleAreaListener
        if (!vf.isValid || vf.fileType != DartFileType.INSTANCE) return@VisibleAreaListener
        val open = FileEditorManager.getInstance(project).openFiles
        if (open.none { it == vf }) return@VisibleAreaListener
        scheduleFill(vf, "scroll")
    }

    private val listener = object : AnalysisServerListenerAdapter() {
        // Intentionally ignore computedHighlights — floods DAS on large/warn-heavy projects.
        // 故意不监听 computedHighlights：大工程/警告多时会打爆 DAS。

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
        Disposer.register(this) {
            worker.shutdownNow()
        }
        val das = DartAnalysisServerService.getInstance(project)
        das.addAnalysisServerListener(listener)
        Disposer.register(this) {
            das.removeAnalysisServerListener(listener)
        }

        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                val editor = event.editor
                if (editor.project != null && editor.project != project) return
                editor.scrollingModel.addVisibleAreaListener(visibleAreaListener, this@DartTypeHintsRefreshService)
                editor.putUserData(VIEWPORT_LISTENER_MARK, true)
            }
        }, this)

        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project != null && editor.project != project) continue
            if (editor.getUserData(VIEWPORT_LISTENER_MARK) == true) continue
            editor.scrollingModel.addVisibleAreaListener(visibleAreaListener, this)
            editor.putUserData(VIEWPORT_LISTENER_MARK, true)
        }
    }

    private fun scheduleFill(virtualFile: VirtualFile, reason: String) {
        if (project.isDisposed || disposed.get()) return
        FlutterHelperPerfLog.debug("typeHints.schedule", "${virtualFile.name} reason=$reason")
        synchronized(pendingFillUrls) {
            pendingFillUrls.add(virtualFile.url)
        }
        fillAlarm.cancelAllRequests()
        fillAlarm.addRequest({ flushFillPending() }, FILL_DEBOUNCE_MS)
    }

    private fun flushFillPending() {
        if (project.isDisposed || disposed.get() || shouldSkipHeavyWork()) return
        val urls = synchronized(pendingFillUrls) {
            pendingFillUrls.toList().also { pendingFillUrls.clear() }
        }
        for (url in urls) {
            val vf = openFileByUrl(url) ?: continue
            enqueueFill(vf)
        }
    }

    private fun openFileByUrl(url: String): VirtualFile? =
        ReadAction.compute<VirtualFile?, RuntimeException> {
            FileEditorManager.getInstance(project).openFiles.firstOrNull { it.url == url }
        }

    private fun shouldSkipHeavyWork(): Boolean =
        PowerSaveMode.isEnabled() || DumbService.isDumb(project)

    fun ensureCached(virtualFile: VirtualFile?) {
        if (virtualFile == null || project.isDisposed || disposed.get() || shouldSkipHeavyWork()) return
        val cache = DartTypeHintsCache.getInstance(project)
        if (cache.hasFile(virtualFile)) return
        scheduleFill(virtualFile, "ensureCached")
    }

    private fun enqueueFill(virtualFile: VirtualFile) {
        val key = virtualFile.url
        if (!queuedUrls.add(key)) {
            FlutterHelperPerfLog.debug("typeHints.queue.dedupe", virtualFile.name)
            return
        }

        val future = worker.submit {
            try {
                if (project.isDisposed || disposed.get() || shouldSkipHeavyWork()) return@submit
                val ready = ReadAction.compute<Boolean, RuntimeException> {
                    !project.isDisposed &&
                        DartAnalysisServerService.getInstance(project).serverReadyForRequest()
                }
                if (!ready) {
                    FlutterHelperPerfLog.debug("typeHints.skip.serverNotReady", virtualFile.name)
                    return@submit
                }

                val range = DartTypeHintsViewport.expandedVisibleRange(project, virtualFile)
                val cache = DartTypeHintsCache.getInstance(project)
                if (cache.isViewportSatisfied(virtualFile, range)) {
                    FlutterHelperPerfLog.viewportSatisfied(virtualFile.name)
                    return@submit
                }

                FlutterHelperPerfLog.fillStarted(virtualFile.name)
                val start = System.nanoTime()
                val changed = DartAnalysisLoadGuard.tryRun {
                    try {
                        cache.fillVisible(virtualFile, range)
                    } catch (_: Exception) {
                        false
                    }
                }
                if (changed == null) {
                    FlutterHelperPerfLog.gateBusy("typeHints.fill")
                    return@submit
                }
                val ms = (System.nanoTime() - start) / 1_000_000L
                FlutterHelperPerfLog.fillDone(virtualFile.name, ms, -1, -1, changed)

                if (!changed || project.isDisposed || disposed.get()) return@submit

                ApplicationManager.getApplication().invokeLater({
                    if (project.isDisposed || disposed.get()) return@invokeLater
                    FlutterHelperPerfLog.daemonRestart(virtualFile.name)
                    refreshInlays(virtualFile)
                }, ModalityState.defaultModalityState())
            } finally {
                queuedUrls.remove(key)
            }
        }
        inflight[key] = future
    }

    @Suppress("UnstableApiUsage")
    private fun refreshInlays(virtualFile: VirtualFile) {
        val psi = ReadAction.compute<DartFile?, RuntimeException> {
            PsiManager.getInstance(project).findFile(virtualFile) as? DartFile
        } ?: return
        DaemonCodeAnalyzer.getInstance(project).restart(psi, REFRESH_REASON)
    }

    override fun dispose() {
        disposed.set(true)
        fillAlarm.cancelAllRequests()
        inflight.values.forEach { it.cancel(true) }
        inflight.clear()
        queuedUrls.clear()
    }

    companion object {
        private const val FILL_DEBOUNCE_MS = 280
        private val REFRESH_REASON = Any()
        private val VIEWPORT_LISTENER_MARK = Key.create<Boolean>("flutter.helper.typeHints.viewportListener")

        fun getInstance(project: Project): DartTypeHintsRefreshService =
            project.getService(DartTypeHintsRefreshService::class.java)
    }
}
