package com.sixsix.flutter.helper.utils

import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.util.PubspecYamlUtil
import java.util.regex.Pattern

/**
 * Matches a pubspec dependency line like `package_name: ^1.2.3` (excludes sdk / version / ref).
 * 匹配 pubspec 依赖行：`package_name: ^1.2.3`（排除 sdk / version / ref）。
 *
 * Adapted from Flutter Enhancement Suite.
 * 改编自 Flutter Enhancement Suite。
 */
private val PUB_DEPENDENCY_PATTERN: Pattern = Pattern.compile(
    """^\s*(?!version|sdk|ref)\S+:\s*[<|=>^]*([0-9]+\.[0-9]+\.[0-9]+\+?\S*)""",
)

/**
 * Whether this line looks like a versioned pub dependency (`name: ^x.y.z`).
 * 判断该行是否像带版本号的 pub 依赖声明（`name: ^x.y.z`）。
 */
fun String.isPubPackageName(): Boolean = PUB_DEPENDENCY_PATTERN.matcher(this).find()

/**
 * Whether this PSI file is a `pubspec.yaml`.
 * 判断该 PSI 文件是否为 `pubspec.yaml`。
 */
fun PsiFile.isPubspecFile(): Boolean {
    val vf = virtualFile ?: return false
    return PubspecYamlUtil.isPubspecFile(vf)
}
