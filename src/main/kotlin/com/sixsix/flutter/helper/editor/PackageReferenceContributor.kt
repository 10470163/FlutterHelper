package com.sixsix.flutter.helper.editor

import com.intellij.openapi.paths.GlobalPathReferenceProvider
import com.intellij.openapi.paths.PathReferenceManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.sixsix.flutter.helper.utils.isPubPackageName
import com.sixsix.flutter.helper.utils.isPubspecFile
import org.jetbrains.yaml.psi.YAMLKeyValue

private const val PUB_PACKAGE_BASE_URL = "https://pub.dev/packages/"

/**
 * Attaches pub.dev URL references to package names in pubspec.yaml.
 * 为 pubspec.yaml 中的依赖包名挂上指向 pub.dev 的 URL 引用。
 *
 * Use IDE Goto Declaration (Ctrl+B / ⌘B) to open the package page.
 * 使用 IDE 自带 Goto Declaration（Ctrl+B / ⌘+B）即可打开包页面。
 *
 * Adapted from Flutter Enhancement Suite (GPL-3.0).
 * 改编自 Flutter Enhancement Suite（GPL-3.0）。
 */
class PackageReferenceContributor : PsiReferenceContributor() {

    private val globalPathProvider by lazy {
        PathReferenceManager.getInstance().globalWebPathReferenceProvider as? GlobalPathReferenceProvider
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLKeyValue::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val provider = globalPathProvider ?: return PsiReference.EMPTY_ARRAY
                    val yamlKeyValue = element as? YAMLKeyValue ?: return PsiReference.EMPTY_ARRAY
                    if (!element.containingFile.isPubspecFile() || !yamlKeyValue.text.isPubPackageName()) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val packageName = yamlKeyValue.keyText
                    val references = mutableListOf<PsiReference>()
                    provider.createUrlReference(
                        yamlKeyValue,
                        PUB_PACKAGE_BASE_URL + packageName,
                        TextRange.allOf(packageName),
                        references,
                    )
                    return references.toTypedArray()
                }
            },
        )
    }
}
