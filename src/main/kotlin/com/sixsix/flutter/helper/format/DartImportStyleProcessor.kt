package com.sixsix.flutter.helper.format

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartImportStatement
import com.jetbrains.lang.dart.util.DartUrlResolver
import com.jetbrains.lang.dart.util.PubspecYamlUtil
import com.sixsix.flutter.helper.settings.FlutterHelperSettings
import com.sixsix.flutter.helper.settings.ImportStyle
import java.nio.file.Path

/**
 * Rewrites in-project Dart imports to Package or Relative style.
 * 项目内 Dart import 风格重写器。
 *
 * Imports under the current package `lib/` become:
 * 将落在当前 package `lib/` 下的 import 转为：
 * - [ImportStyle.PACKAGE]: `package:<name>/<path-under-lib>.dart`
 * - [ImportStyle.RELATIVE]: path relative to the current file
 *
 * Uses Document text replacement (more stable than PSI replace) with a manual relative-path fallback.
 * 使用 Document 文本替换（比 PSI replace 更稳），并手动解析相对路径作为 resolver 的回退。
 */
object DartImportStyleProcessor {

    /**
     * Rewrites in-project Dart imports using [FlutterHelperSettings].
     * 按 [FlutterHelperSettings] 将项目内 Dart import 重写为 Package 或 Relative 风格。
     *
     * No-op for `dart:` and third-party `package:` URIs.
     * 对 `dart:` 与第三方 `package:` 不做处理。
     */
    fun rewrite(file: PsiFile) {
        val project = file.project
        val virtualFile = file.virtualFile ?: return
        val document = file.viewProvider.document ?: return
        val pubspec = PubspecYamlUtil.findPubspecYamlFile(project, virtualFile) ?: return
        val packageName = PubspecYamlUtil.getDartProjectName(pubspec) ?: return
        val packageRoot = pubspec.parent ?: return
        val libDir = packageRoot.findChild("lib") ?: return
        val settings = FlutterHelperSettings.getInstance()
        val resolver = DartUrlResolver.getInstance(project, virtualFile)

        val manager = PsiDocumentManager.getInstance(project)
        manager.doPostponedOperationsAndUnblockDocument(document)
        manager.commitDocument(document)
        val psiFile = manager.getPsiFile(document) ?: return

        // 从后往前替换，避免偏移错乱
        val imports = psiFile.children.filterIsInstance<DartImportStatement>().toList().asReversed()
        for (importStatement in imports) {
            if (!importStatement.isValid) continue
            val uri = importStatement.uriString.trim()
            if (uri.isBlank() || uri.startsWith("dart:")) continue
            // 第三方 package 不动
            if (uri.startsWith("package:") && !uri.startsWith("package:$packageName/")) continue

            val target = resolveTarget(resolver, virtualFile, uri) ?: continue
            if (!VfsUtilCore.isAncestor(libDir, target, false)) continue

            val relativeFromLib = VfsUtilCore.findRelativePath(libDir, target, '/') ?: continue
            val desired = when (settings.importStyle) {
                ImportStyle.PACKAGE -> "package:$packageName/$relativeFromLib"
                ImportStyle.RELATIVE -> relativePath(virtualFile.parent.path, target.path)
            }
            if (desired == uri) continue

            val range = importStatement.textRange
            val oldText = document.getText(range)
            val newText = oldText
                .replace("'$uri'", "'$desired'")
                .replace("\"$uri\"", "\"$desired\"")
            if (newText == oldText) continue
            document.replaceString(range.startOffset, range.endOffset, newText)
        }
    }

    /**
     * Builds `package:<name>/<path>` from a path relative to `lib/`.
     * 根据相对 `lib/` 的路径拼出 `package:<name>/<path>`。
     */
    internal fun packageImportUri(packageName: String, relativeFromLib: String): String {
        val path = relativeFromLib.trim().trimStart('/')
        return "package:$packageName/$path"
    }

    /**
     * Resolves the file targeted by an import URI.
     * 解析 import URI 指向的文件。
     *
     * Prefers [DartUrlResolver]; relative paths fall back to VFS lookup from the current file directory.
     * 优先 [DartUrlResolver]；相对路径再回退到基于当前文件目录的 VFS 查找。
     */
    private fun resolveTarget(
        resolver: DartUrlResolver,
        currentFile: VirtualFile,
        uri: String,
    ): VirtualFile? {
        resolver.findFileByDartUrl(uri)?.let { return it }

        if (uri.startsWith("package:") || uri.startsWith("dart:")) return null
        val parent = currentFile.parent ?: return null
        val cleaned = uri.removePrefix("./")
        parent.findFileByRelativePath(cleaned)?.let { return it }
        return VfsUtil.findRelativeFile(cleaned, parent)
    }

    /**
     * Relative path matching IDEA / Dart conventions: never use a `./` prefix.
     * 相对路径与 IDEA / Dart 惯例一致：不写 `./` 前缀。
     *
     * - Same dir: `other.dart` / 同目录：`other.dart`
     * - Nested: `data/providers.dart` (not `./data/...`) / 子目录
     * - Parent: `../widgets/card.dart` / 上级
     */
    internal fun relativePath(fromDir: String, toFile: String): String {
        val from = Path.of(fromDir).normalize()
        val to = Path.of(toFile).normalize()
        return from.relativize(to).toString().replace('\\', '/')
    }
}
