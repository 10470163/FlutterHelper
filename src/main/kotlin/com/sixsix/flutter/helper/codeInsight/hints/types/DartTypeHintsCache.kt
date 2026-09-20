package com.sixsix.flutter.helper.codeInsight.hints.types

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches identifier offset → inferred type per file.
 * 按文件缓存标识符偏移 → 推断类型。
 *
 * Collect reads cache only; hover runs asynchronously via [DartAnalysisHoverCompat]
 * (never calls removed DAS.analysis_getHover directly — Dart 509+ / Plugin Verifier).
 * collect 只读缓存；hover 经兼容层异步填充（不直接调用已移除的 DAS.analysis_getHover）。
 */
@Service(Service.Level.PROJECT)
class DartTypeHintsCache(private val project: Project) {

    private data class FileCache(
        val modificationStamp: Long,
        val typesByOffset: Map<Int, String>,
    )

    private val caches = ConcurrentHashMap<String, FileCache>()

    fun getType(virtualFile: VirtualFile, textOffset: Int): String? {
        val cache = caches[virtualFile.url] ?: return null
        if (cache.modificationStamp != virtualFile.modificationStamp) return null
        return cache.typesByOffset[textOffset]
    }

    /** 是否已有该文件（可能为空 map）的有效缓存。 */
    fun hasFile(virtualFile: VirtualFile): Boolean {
        val cache = caches[virtualFile.url] ?: return false
        return cache.modificationStamp == virtualFile.modificationStamp
    }

    fun invalidate(virtualFile: VirtualFile) {
        caches.remove(virtualFile.url)
    }

    /**
     * 在读锁下遍历无显式类型的变量/形参，批量 hover 填缓存。
     * 可在后台线程调用：PSI / Analysis Server 访问均包在短 ReadAction 中。
     * @return 是否写入了至少一个类型
     */
    fun rebuild(virtualFile: VirtualFile): Boolean {
        if (project.isDisposed) return false
        val das = DartAnalysisServerService.getInstance(project)
        if (!serverReady()) return false

        val snapshot = ReadAction.compute<Pair<Long, List<Pair<Int, String>>>?, RuntimeException> {
            if (project.isDisposed) return@compute null
            val psi = PsiManager.getInstance(project).findFile(virtualFile) as? DartFile
                ?: return@compute null
            val stamp = virtualFile.modificationStamp
            val targets = mutableListOf<Pair<Int, String>>()

            PsiTreeUtil.processElements(psi) { element ->
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
                true
            }
            stamp to targets
        } ?: return false

        val (stamp, targets) = snapshot
        if (targets.isEmpty()) {
            caches[virtualFile.url] = FileCache(stamp, emptyMap())
            return false
        }

        val types = LinkedHashMap<Int, String>()
        for ((offset, name) in targets) {
            if (project.isDisposed || !serverReady()) break
            val type = resolveType(das, virtualFile, offset, name) ?: continue
            types[offset] = type
        }

        // 文件在填充过程中被改过则丢弃
        val currentStamp = ReadAction.compute<Long, RuntimeException> { virtualFile.modificationStamp }
        if (currentStamp != stamp) return false
        caches[virtualFile.url] = FileCache(stamp, types)
        return types.isNotEmpty()
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
        // Analysis Server 访问需在 read-action 内（IDEA 线程模型校验）
        return ReadAction.compute<String?, RuntimeException> {
            if (project.isDisposed || !das.serverReadyForRequest()) return@compute null
            val offsets = listOf(
                textOffset,
                das.getConvertedOffset(virtualFile, textOffset),
            ).distinct().filter { it >= 0 }

            for (offset in offsets) {
                val hover = try {
                    DartAnalysisHoverCompat.getHovers(das, virtualFile, offset).firstOrNull()
                } catch (_: Exception) {
                    null
                } ?: continue
                DartTypeHintParser.typeFromHover(hover, name)?.let { return@compute it }
            }
            null
        }
    }

    companion object {
        fun getInstance(project: Project): DartTypeHintsCache =
            project.getService(DartTypeHintsCache::class.java)
    }
}
