package com.sixsix.flutter.helper.codeInsight.codevision

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived Code Vision hint cache keyed by file stamp + element offset.
 * 按文件 stamp + 元素偏移缓存 Code Vision 文案。
 *
 * Does not store "busy skip" misses — those must be retried next pass.
 * 不缓存「忙时跳过」的空结果，下次仍可重试。
 */
@Service(Service.Level.PROJECT)
class DartCodeVisionHintCache {
    private data class Key(
        val kind: String,
        val fileUrl: String,
        val stamp: Long,
        val offset: Int,
    )

    private data class Entry(val hint: String?)

    private val hints = ConcurrentHashMap<Key, Entry>()

    fun getIfPresent(kind: String, element: PsiElement): EntryLookup {
        val key = keyOf(kind, element) ?: return EntryLookup.Absent
        val entry = hints[key] ?: return EntryLookup.Absent
        return EntryLookup.Present(entry.hint)
    }

    fun put(kind: String, element: PsiElement, hint: String?) {
        val key = keyOf(kind, element) ?: return
        val file = element.containingFile?.virtualFile ?: return
        val stamp = file.modificationStamp
        if (hints.size > MAX_ENTRIES) {
            hints.keys.removeIf { it.fileUrl == file.url && it.stamp != stamp }
            if (hints.size > MAX_ENTRIES) hints.clear()
        }
        hints[key] = Entry(hint)
    }

    fun invalidate(file: VirtualFile) {
        val url = file.url
        hints.keys.removeIf { it.fileUrl == url }
    }

    private fun keyOf(kind: String, element: PsiElement): Key? {
        val file = element.containingFile?.virtualFile ?: return null
        val offset = element.textRange?.startOffset ?: return null
        return Key(kind, file.url, file.modificationStamp, offset)
    }

    sealed class EntryLookup {
        data object Absent : EntryLookup()
        data class Present(val hint: String?) : EntryLookup()
    }

    companion object {
        private const val MAX_ENTRIES = 2_000

        fun getInstance(project: Project): DartCodeVisionHintCache =
            project.getService(DartCodeVisionHintCache::class.java)
    }
}
