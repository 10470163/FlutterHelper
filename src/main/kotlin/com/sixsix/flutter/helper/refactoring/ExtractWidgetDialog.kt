package com.sixsix.flutter.helper.refactoring

import com.intellij.codeInsight.actions.OptimizeImportsProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.ide.actions.DartStyleAction
import com.jetbrains.lang.dart.ide.refactoring.ServerRefactoringDialog
import com.jetbrains.lang.dart.psi.DartClassDefinition
import com.jetbrains.lang.dart.util.DartElementGenerator
import com.jetbrains.lang.dart.util.PubspecYamlUtil
import com.sixsix.flutter.helper.FlutterHelperBundle
import com.sixsix.flutter.helper.format.DartImportStyleProcessor
import com.sixsix.flutter.helper.utils.createImportStatement
import com.sixsix.flutter.helper.utils.extractDartImportStatements
import com.sixsix.flutter.helper.utils.toSnakeCase
import io.flutter.refactoring.ExtractWidgetRefactoring
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

/**
 * Where to place the extracted Widget: current file or a new file.
 * 提取 Widget 的生成位置：当前文件或新文件。
 */
enum class ExtractTarget {
    CURRENT_FILE,
    NEW_FILE,
}

/**
 * How the new file is referenced: `import` or `part of`.
 * 提取到新文件时的引用方式：`import` 或 `part of`。
 */
enum class ExtractLinkStyle {
    IMPORT,
    PART_OF,
}

/**
 * Extract Widget dialog.
 * 提取 Widget 对话框。
 *
 * New-file flow (aligned with FES):
 * 新文件流程对齐 FES：
 * 1. Register a PSI listener in doAction / doAction 时注册 PSI 监听
 * 2. When a new `class ...` appears in the current file, move it and add import/part
 *    检测到当前文件新增 `class ...` 后挪到新文件并补 import/part
 *
 * Hardening vs FES: listener is scoped to the project lifetime (dialog dispose is too early),
 * plus Alarm retries by class name for Analysis Server async timing.
 * 相对 FES 的加固：监听挂在 project 生命周期上，并用 Alarm 按类名多次回退扫描。
 */
internal class ExtractWidgetDialog(
    project: Project,
    val file: VirtualFile,
    var editor: Editor?,
    myRefactoring: ExtractWidgetRefactoring,
) : ServerRefactoringDialog<ExtractWidgetRefactoring>(project, editor, myRefactoring) {

    private val myNameField = JTextField()
    private val myFileNameField = JTextField()
    private val currentFileRadio =
        JRadioButton(FlutterHelperBundle.message("dialog.extractWidget.target.current"), false)
    private val newFileRadio =
        JRadioButton(FlutterHelperBundle.message("dialog.extractWidget.target.newFile"), true)
    private val importRadio =
        JRadioButton(FlutterHelperBundle.message("dialog.extractWidget.link.import"), true)
    private val partOfRadio =
        JRadioButton(FlutterHelperBundle.message("dialog.extractWidget.link.partOf"), false)

    private val myWidgetTreeChangeListener = WidgetTreeChangeListener()
    private val listenerDisposable = Disposer.newDisposable("FlutterHelperExtractWidget")
    private val moved = AtomicBoolean(false)
    private val listeningFinished = AtomicBoolean(false)
    private val retryAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, listenerDisposable)
    private var syncingFileName = false

    init {
        title = FlutterHelperBundle.message("dialog.extractWidget.title")
        init()
        Disposer.register(project, listenerDisposable)

        ButtonGroup().apply {
            add(currentFileRadio)
            add(newFileRadio)
        }
        ButtonGroup().apply {
            add(importRadio)
            add(partOfRadio)
        }

        myNameField.text = getWidgetNameSuggestion()
        myNameField.selectAll()
        updateFileNameFromWidgetName()

        myNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateRefactoringOptions()
                if (!syncingFileName) updateFileNameFromWidgetName()
            }
        })
        currentFileRadio.addActionListener { updateLinkEnabled() }
        newFileRadio.addActionListener { updateLinkEnabled() }
        updateLinkEnabled()
        updateRefactoringOptions()
    }

    private fun updateLinkEnabled() {
        val newFile = newFileRadio.isSelected
        importRadio.isEnabled = newFile
        partOfRadio.isEnabled = newFile
        myFileNameField.isEnabled = newFile
    }

    private fun updateFileNameFromWidgetName() {
        syncingFileName = true
        try {
            val base = myNameField.text.trim().ifEmpty {
                FlutterHelperBundle.message("dialog.extractWidget.defaultName")
            }
            myFileNameField.text = base.toSnakeCase() + ".dart"
        } finally {
            syncingFileName = false
        }
    }

    private fun getWidgetNameSuggestion(): String {
        editor?.caretModel?.currentCaret?.offset?.let { offset ->
            val psiElement = PsiManager.getInstance(project).findFile(file)?.findElementAt(offset) ?: return@let
            val classElement = PsiTreeUtil.getParentOfType(psiElement, DartClassDefinition::class.java) ?: return@let
            val widgetName = psiElement.text.replaceFirstChar { it.uppercase() }.split(".")[0]
                .filter { it.isLetterOrDigit() || it == '_' }
            if (widgetName.isNotBlank()) {
                return (classElement.name ?: "") + widgetName.replaceFirstChar { it.uppercase() }
            }
        }
        return FlutterHelperBundle.message("dialog.extractWidget.defaultName")
    }

    private fun updateRefactoringOptions() {
        myRefactoring.setName(
            myNameField.text.trim().ifEmpty {
                FlutterHelperBundle.message("dialog.extractWidget.defaultName")
            },
        )
        myRefactoring.sendOptions()
    }

    private fun extractTarget(): ExtractTarget =
        if (newFileRadio.isSelected) ExtractTarget.NEW_FILE else ExtractTarget.CURRENT_FILE

    private fun linkStyle(): ExtractLinkStyle =
        if (partOfRadio.isSelected) ExtractLinkStyle.PART_OF else ExtractLinkStyle.IMPORT

    override fun doAction() {
        updateRefactoringOptions()
        val wantNewFile = extractTarget() == ExtractTarget.NEW_FILE
        moved.set(false)

        if (wantNewFile) {
            PsiManager.getInstance(project)
                .addPsiTreeChangeListener(myWidgetTreeChangeListener, listenerDisposable)
        }

        super.doAction()
        FileDocumentManager.getInstance().saveAllDocuments()

        if (wantNewFile) {
            scheduleMoveRetries()
        }
    }

    /** Analysis Server 可能异步插入 class，延迟多次按名称扫描。 */
    private fun scheduleMoveRetries() {
        val delaysMs = intArrayOf(50, 150, 350, 700, 1200, 2000)
        for (delay in delaysMs) {
            retryAlarm.addRequest({
                if (!project.isDisposed && !moved.get()) {
                    moveExtractedClassByName()
                }
            }, delay)
        }
    }

    override fun createCenterPanel(): JComponent? = null

    override fun createNorthPanel(): JPanel = JPanel(GridBagLayout()).apply {
        var row = 0

        fun addRow(label: String, component: JComponent) {
            add(
                JLabel(label),
                GridBagConstraints().apply {
                    insets = JBUI.insetsBottom(SMALL_PADDING)
                    gridx = 0
                    gridy = row
                    anchor = GridBagConstraints.WEST
                },
            )
            add(
                component,
                GridBagConstraints().apply {
                    insets = JBUI.insets(0, SMALL_PADDING, SMALL_PADDING, 0)
                    gridx = 1
                    gridy = row
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    anchor = GridBagConstraints.WEST
                },
            )
            row++
        }

        myNameField.preferredSize = Dimension(NAME_FIELD_WIDTH, myNameField.preferredSize.height)
        myFileNameField.preferredSize = Dimension(NAME_FIELD_WIDTH, myFileNameField.preferredSize.height)

        addRow(FlutterHelperBundle.message("dialog.extractWidget.nameLabel"), myNameField)
        addRow(FlutterHelperBundle.message("dialog.extractWidget.fileNameLabel"), myFileNameField)
        addRow(
            FlutterHelperBundle.message("dialog.extractWidget.targetLabel"),
            JPanel().apply {
                add(currentFileRadio)
                add(newFileRadio)
            },
        )
        addRow(
            FlutterHelperBundle.message("dialog.extractWidget.linkLabel"),
            JPanel().apply {
                add(importRadio)
                add(partOfRadio)
            },
        )
    }

    override fun getPreferredFocusedComponent(): JComponent = myNameField

    private fun normalizeDartFileName(raw: String): String {
        var name = raw.trim().ifEmpty { myNameField.text.toSnakeCase() + ".dart" }
        if (!name.endsWith(".dart")) name += ".dart"
        return name
    }

    private fun moveExtractedClassByName(preferredChild: PsiElement? = null) {
        if (moved.get()) return

        val originalFile = PsiManager.getInstance(project).findFile(file) ?: return
        val expectedName = myNameField.text.trim()

        val classElement = when {
            preferredChild is DartClassDefinition &&
                (expectedName.isEmpty() || preferredChild.name == expectedName) -> preferredChild
            preferredChild != null ->
                PsiTreeUtil.findChildOfType(preferredChild, DartClassDefinition::class.java)
                    ?.takeIf { expectedName.isEmpty() || it.name == expectedName }
            else -> null
        } ?: PsiTreeUtil.findChildrenOfType(originalFile, DartClassDefinition::class.java)
            .firstOrNull { it.name == expectedName }
            ?: return

        if (!moved.compareAndSet(false, true)) return

        val fileName = normalizeDartFileName(myFileNameField.text)
        when (linkStyle()) {
            ExtractLinkStyle.IMPORT -> moveWithImport(originalFile, fileName, classElement)
            ExtractLinkStyle.PART_OF -> moveWithPartOf(originalFile, fileName, classElement)
        }
    }

    private fun moveWithImport(originalFile: PsiFile, fileName: String, classElement: DartClassDefinition) {
        runUndoTransparentWriteAction {
            finishListening()
            val pubspecFile = PubspecYamlUtil.findPubspecYamlFile(project, file)
            if (pubspecFile == null) {
                PopupUtil.showBalloonForActiveComponent(
                    FlutterHelperBundle.message("dialog.extractWidget.pubspecMissing"),
                    MessageType.ERROR,
                )
                moved.set(false)
                return@runUndoTransparentWriteAction
            }

            val newFile = originalFile.containingDirectory?.findFile(fileName)
                ?: originalFile.containingDirectory?.createFile(fileName)
            if (newFile == null) {
                moved.set(false)
                return@runUndoTransparentWriteAction
            }

            val projectName = PubspecYamlUtil.getDartProjectName(pubspecFile)
            val libDir = pubspecFile.parent?.findChild("lib")
            val relativeFromLib = libDir?.let { VfsUtilCore.findRelativePath(it, newFile.virtualFile, '/') }
            if (projectName == null || relativeFromLib == null) {
                PopupUtil.showBalloonForActiveComponent(
                    FlutterHelperBundle.message("dialog.extractWidget.pubspecMissing"),
                    MessageType.ERROR,
                )
                moved.set(false)
                return@runUndoTransparentWriteAction
            }

            val importUri = DartImportStyleProcessor.packageImportUri(projectName, relativeFromLib)
            val importStatementOrig = project.createImportStatement(importUri)
            val space = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n")

            // FES moves imports; we copy so the original file keeps its dependencies.
            // FES 会 move import；这里 copy，避免抽空原文件依赖。
            originalFile.extractDartImportStatements().forEach { importStatement ->
                newFile.addAfter(space, newFile.add(importStatement.copy()))
            }
            originalFile.addAfter(space, originalFile.addBefore(importStatementOrig, originalFile.firstChild))
            // Match FES: add then delete (add may copy).
            // 与 FES 一致：add 再 delete（add 可能为 copy）。
            newFile.add(classElement)
            if (classElement.isValid) {
                classElement.delete()
            }
            formatFiles(mutableListOf(newFile, originalFile))
        }
    }

    private fun moveWithPartOf(originalFile: PsiFile, fileName: String, classElement: DartClassDefinition) {
        runUndoTransparentWriteAction {
            finishListening()
            val newFile = originalFile.containingDirectory?.findFile(fileName)
                ?: originalFile.containingDirectory?.createFile(fileName)
            if (newFile == null) {
                moved.set(false)
                return@runUndoTransparentWriteAction
            }

            val space = PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n")
            val partOf = DartElementGenerator.createDummyFile(
                project,
                "part of '${originalFile.name}';",
            ).firstChild
            val partDirective = DartElementGenerator.createDummyFile(
                project,
                "part '$fileName';",
            ).firstChild

            newFile.add(partOf)
            newFile.add(space)
            newFile.add(classElement)
            if (classElement.isValid) {
                classElement.delete()
            }

            val imports = originalFile.extractDartImportStatements()
            val anchor = imports.lastOrNull() ?: originalFile.firstChild
            originalFile.addAfter(space, originalFile.addAfter(partDirective, anchor))
            formatFiles(mutableListOf(newFile, originalFile))
        }
    }

    private fun finishListening() {
        if (!listeningFinished.compareAndSet(false, true)) return
        retryAlarm.cancelAllRequests()
        Disposer.dispose(listenerDisposable)
    }

    private fun formatFiles(filesToFormat: MutableList<PsiFile>) {
        DartStyleAction.runDartfmt(project, filesToFormat.map { it.virtualFile })
        OptimizeImportsProcessor(project, filesToFormat.toTypedArray(), null).run()
    }

    /**
     * Aligned with FES: when a child starting with `class` is added to the current file, move it.
     * 对齐 FES：检测到当前文件新增以 `class` 开头的子节点后触发挪文件。
     */
    inner class WidgetTreeChangeListener : PsiTreeChangeAdapter() {
        override fun childAdded(event: PsiTreeChangeEvent) {
            if (moved.get()) return
            val eventFile = event.file ?: return
            val child = event.child ?: return
            if (eventFile.virtualFile.path != file.path) return
            if (!child.text.trimStart().startsWith("class")) return

            val expectedName = myNameField.text.trim()
            if (expectedName.isNotEmpty() && !child.text.contains(expectedName)) return

            file.refresh(true, true) {
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed && !moved.get()) {
                        moveExtractedClassByName(child)
                    }
                }, ModalityState.defaultModalityState())
            }
        }
    }
}
