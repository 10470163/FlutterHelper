package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.sixsix.flutter.helper.analysis.DartAnalysisLoadGuard
import com.sixsix.flutter.helper.perf.FlutterHelperPerfLog
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Viewport-first type cache: only hover targets near the visible range; merge fills.
 * 可见区优先类型缓存：只 hover 可见附近目标，多次填充合并。
 */
@Service(Service.Level.PROJECT)
class DartTypeHintsCache(private val project: Project) {

    private data class FileCache(
        val modificationStamp: Long,
        val typesByOffset: Map<Int, String>,
        val attemptedOffsets: Set<Int>,
    )

    private val caches = ConcurrentHashMap<String, FileCache>()

    fun getType(virtualFile: VirtualFile, textOffset: Int): String? {
        val cache = caches[virtualFile.url] ?: return null
        if (cache.modificationStamp != virtualFile.modificationStamp) return null
        return cache.typesByOffset[textOffset]
    }

    fun hasFile(virtualFile: VirtualFile): Boolean {
        val cache = caches[virtualFile.url] ?: return false
        return cache.modificationStamp == virtualFile.modificationStamp
    }

    /**
     * True when every untyped target in [visibleRange] was already attempted for this stamp.
     * 可见区内无类型目标均已尝试过（本次 stamp）则为 true，可跳过填充。
     */
    fun isViewportSatisfied(virtualFile: VirtualFile, visibleRange: TextRange?): Boolean {
        return ReadAction.compute<Boolean, RuntimeException> {
            val cache = caches[virtualFile.url] ?: return@compute false
            if (cache.modificationStamp != virtualFile.modificationStamp) return@compute false
            val psi = PsiManager.getInstance(project).findFile(virtualFile) as? DartFile
                ?: return@compute true
            val range = visibleRange ?: TextRange(0, minOf(psi.textLength, HEAD_WINDOW_CHARS))
            val targets = collectTargetsInRange(psi, range)
            targets.isEmpty() || targets.all { it.first in cache.attemptedOffsets }
        }
    }

    fun invalidate(virtualFile: VirtualFile) {
        caches.remove(virtualFile.url)
    }

    fun fillVisible(virtualFile: VirtualFile, visibleRange: TextRange?): Boolean {
        if (project.isDisposed) return false
        val das = DartAnalysisServerService.getInstance(project)
        if (!serverReady()) return false

        val previous = caches[virtualFile.url]
        val snapshot = ReadAction.compute<Snapshot?, RuntimeException> {
            if (project.isDisposed) return@compute null
            val psi = PsiManager.getInstance(project).findFile(virtualFile) as? DartFile
                ?: return@compute null
            val stamp = virtualFile.modificationStamp
            val docLen = psi.textLength
            val range = visibleRange ?: TextRange(0, minOf(docLen, HEAD_WINDOW_CHARS))
            Snapshot(stamp, collectTargetsInRange(psi, range))
        } ?: return false

        val (stamp, targets) = snapshot
        val baseTypes = if (previous != null && previous.modificationStamp == stamp) {
            LinkedHashMap(previous.typesByOffset)
        } else {
            LinkedHashMap()
        }
        val attempted = if (previous != null && previous.modificationStamp == stamp) {
            HashSet(previous.attemptedOffsets)
        } else {
            HashSet()
        }

        if (targets.isEmpty()) {
            caches[virtualFile.url] = FileCache(stamp, baseTypes, attempted)
            return previous == null ||
                previous.modificationStamp != stamp ||
                previous.typesByOffset != baseTypes
        }

        val pending = targets.filter { it.first !in attempted }.take(MAX_TARGETS_PER_FILL)
        if (pending.isEmpty()) {
            caches[virtualFile.url] = FileCache(stamp, baseTypes, attempted)
            return false
        }

        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FILL_BUDGET_MS)
        var changed = false
        var resolved = 0
        for ((offset, name) in pending) {
            if (project.isDisposed || Thread.currentThread().isInterrupted) break
            if (System.nanoTime() > deadline) {
                FlutterHelperPerfLog.debug("typeHints.fill.budget", "pendingLeft=${pending.size - attempted.size}")
                break
            }
            try {
                ProgressManager.checkCanceled()
            } catch (_: ProcessCanceledException) {
                break
            }
            if (!stampStillValid(virtualFile, stamp)) return false
            if (!serverReady()) break

            attempted.add(offset)
            val type = resolveType(das, virtualFile, offset, name)
            if (type != null) {
                resolved++
                val old = baseTypes.put(offset, type)
                if (old != type) changed = true
            }
        }

        if (!stampStillValid(virtualFile, stamp)) return false
        val beforeTypes = previous?.takeIf { it.modificationStamp == stamp }?.typesByOffset
        caches[virtualFile.url] = FileCache(stamp, baseTypes, attempted)
        FlutterHelperPerfLog.debug(
            "typeHints.fill.stats",
            "file=${virtualFile.name} pending=${pending.size} resolved=$resolved changed=$changed",
        )
        return changed || beforeTypes != baseTypes
    }

    /**
     * Walk only the PSI subtree covering [range], not the entire file.
     * 只遍历覆盖 [range] 的 PSI 子树，不扫整文件。
     */
    private fun collectTargetsInRange(psi: DartFile, range: TextRange): List<Pair<Int, String>> {
        val targets = ArrayList<Pair<Int, String>>(16)
        val root = subtreeCovering(psi, range)
        PsiTreeUtil.processElements(root) { element ->
            val er = element.textRange ?: return@processElements true
            if (er.endOffset < range.startOffset || er.startOffset > range.endOffset) {
                return@processElements true
            }
            when (element) {
                is DartVarAccessDeclaration -> {
                    if (element.type == null) {
                        val name = element.componentName
                        targets += name.textOffset to (name.name ?: "")
                    }
                }
                is DartSimpleFormalParameter -> {
                    if (element.type == null) {
                        val name = element.componentName
                        targets += name.textOffset to (name.name ?: "")
                    }
                }
            }
            targets.size < MAX_TARGETS_PER_FILL * 2
        }
        val center = (range.startOffset + range.endOffset) / 2
        return targets.sortedBy { kotlin.math.abs(it.first - center) }
    }

    private fun subtreeCovering(psi: DartFile, range: TextRange): PsiElement {
        val start = psi.findElementAt(range.startOffset) ?: return psi
        val endOffset = (range.endOffset - 1).coerceAtLeast(range.startOffset)
        val end = psi.findElementAt(endOffset) ?: start
        return PsiTreeUtil.findCommonParent(start, end) ?: psi
    }

    private fun stampStillValid(virtualFile: VirtualFile, stamp: Long): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            !project.isDisposed && virtualFile.modificationStamp == stamp
        }

    private fun serverReady(): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            !project.isDisposed && DartAnalysisServerService.getInstance(project).serverReadyForRequest()
        }

    private fun resolveType(
        das: DartAnalysisServerService,
        virtualFile: VirtualFile,
        textOffset: Int,
        name: String,
    ): String? {
        val offset = try {
            ReadAction.compute<Int, RuntimeException> {
                if (project.isDisposed || !das.serverReadyForRequest()) return@compute -1
                // Prefer converted offset only; one hover round-trip max.
                // 优先转换后偏移，最多一次 hover。
                das.getConvertedOffset(virtualFile, textOffset).takeIf { it >= 0 } ?: textOffset
            }
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: Exception) {
            textOffset
        }
        if (offset < 0) return null
        if (!DartAnalysisLoadGuard.tryAcquireHoverSlot(HOVER_MIN_INTERVAL_MS)) {
            FlutterHelperPerfLog.hoverSkippedInterval()
            return null
        }

        val hover = try {
            DartAnalysisHoverCompat.getHovers(das, virtualFile, offset).firstOrNull()
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: Exception) {
            null
        } ?: return null

        FlutterHelperPerfLog.hoverOk()
        return try {
            ReadAction.compute<String?, RuntimeException> {
                DartTypeHintParser.typeFromHover(hover, name)
            }
        } catch (_: ProcessCanceledException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private data class Snapshot(
        val stamp: Long,
        val targets: List<Pair<Int, String>>,
    )

    companion object {
        private const val MAX_TARGETS_PER_FILL = 12
        private const val FILL_BUDGET_MS = 160L
        private const val HEAD_WINDOW_CHARS = 2_500
        private const val HOVER_MIN_INTERVAL_MS = 55L

        fun getInstance(project: Project): DartTypeHintsCache =
            project.getService(DartTypeHintsCache::class.java)
    }
}
