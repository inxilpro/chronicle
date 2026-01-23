package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

class ChronicleConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var jsonRadioButton: JBRadioButton? = null
    private var markdownRadioButton: JBRadioButton? = null
    private var clipboardRadioButton: JBRadioButton? = null
    private var fileRadioButton: JBRadioButton? = null
    private var promptTextArea: JBTextArea? = null
    private var promptPanel: JPanel? = null
    private var waveformCheckbox: JBCheckBox? = null
    private var templateList: JBList<TemplateListItem>? = null
    private var templateListModel: DefaultListModel<TemplateListItem>? = null

    private val editingTemplates: MutableList<PromptTemplate> = mutableListOf()
    private var selectedTemplateId: String? = null
    private var isRemovingTemplate: Boolean = false
    private var isInitializing: Boolean = false

    override fun getDisplayName(): String = "Chronicle"

    override fun createComponent(): JComponent {
        jsonRadioButton = JBRadioButton("Raw JSON")
        markdownRadioButton = JBRadioButton("Markdown prompt")

        val formatButtonGroup = ButtonGroup()
        formatButtonGroup.add(jsonRadioButton)
        formatButtonGroup.add(markdownRadioButton)

        val formatPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            add(jsonRadioButton)
            add(Box.createHorizontalStrut(16))
            add(markdownRadioButton)
        }

        clipboardRadioButton = JBRadioButton("Copy to clipboard")
        fileRadioButton = JBRadioButton("Save to file")

        val destinationButtonGroup = ButtonGroup()
        destinationButtonGroup.add(clipboardRadioButton)
        destinationButtonGroup.add(fileRadioButton)

        val destinationPanel = JPanel().apply {
            layout = FlowLayout(FlowLayout.LEFT, 0, 0)
            add(clipboardRadioButton)
            add(Box.createHorizontalStrut(16))
            add(fileRadioButton)
        }

        templateListModel = DefaultListModel()
        templateList = JBList<TemplateListItem>(templateListModel!!).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = TemplateListCellRenderer()
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    saveCurrentTemplateContent()
                    selectedTemplateId = (selectedValue as? TemplateListItem)?.id
                    loadSelectedTemplateContent()
                }
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        renameSelectedTemplate()
                    }
                }
            })
        }

        val toolbarDecorator = ToolbarDecorator.createDecorator(templateList!!)
            .setAddAction { addTemplate() }
            .setRemoveAction { removeSelectedTemplate() }
            .setRemoveActionUpdater { canRemoveTemplate() }
            .disableUpDownActions()

        val templateListPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(200, 150)
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }

        val renameButton = JButton("Rename").apply {
            addActionListener { renameSelectedTemplate() }
        }

        val templateControlPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JLabel("Prompt templates:").apply {
                border = JBUI.Borders.emptyBottom(4)
            }, BorderLayout.NORTH)
            add(templateListPanel, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(renameButton)
            }, BorderLayout.SOUTH)
        }

        promptTextArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 20
            font = JBUI.Fonts.create("Monospaced", 12)
        }

        val scrollPane = JBScrollPane(promptTextArea).apply {
            preferredSize = Dimension(600, 400)
        }

        val promptEditorPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JLabel("Template content (use {{SESSION_JSON}} as placeholder):").apply {
                border = JBUI.Borders.emptyBottom(4)
            }, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val resetButton = com.intellij.ui.components.JBLabel("<html><a href='#'>Reset to default</a></html>").apply {
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            border = JBUI.Borders.emptyTop(8)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    selectedTemplateId?.let { id ->
                        editingTemplates.find { it.id == id }?.let { template ->
                            if (template.id == ChronicleSettings.DEFAULT_TEMPLATE_ID) {
                                promptTextArea?.text = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
                            }
                        }
                    }
                }
            })
        }

        promptPanel = JPanel(BorderLayout()).apply {
            add(templateControlPanel, BorderLayout.NORTH)
            add(promptEditorPanel, BorderLayout.CENTER)
            add(resetButton, BorderLayout.SOUTH)
        }

        waveformCheckbox = JBCheckBox("Show audio waveform visualization (for debugging)")

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Export format:", formatPanel)
            .addLabeledComponent("Export destination:", destinationPanel)
            .addComponent(promptPanel!!)
            .addSeparator()
            .addComponent(waveformCheckbox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        jsonRadioButton?.addActionListener { updatePromptVisibility() }
        markdownRadioButton?.addActionListener { updatePromptVisibility() }

        reset()

        return mainPanel!!
    }

    private fun addTemplate() {
        val name = Messages.showInputDialog(
            project,
            "Enter template name:",
            "Add Template",
            null,
            "New Template",
            null
        ) ?: return

        if (name.isBlank()) {
            Messages.showErrorDialog(project, "Template name cannot be empty.", "Invalid Name")
            return
        }

        val newTemplate = PromptTemplate(UUID.randomUUID().toString(), name, ChronicleSettings.DEFAULT_MARKDOWN_PROMPT)
        editingTemplates.add(newTemplate)
        refreshTemplateList()
        selectTemplateInList(newTemplate.id)
    }

    private fun removeSelectedTemplate() {
        val selected = templateList?.selectedValue ?: return
        if (!canRemoveTemplate()) return

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete the template \"${selected.name}\"?",
            "Delete Template",
            Messages.getQuestionIcon()
        )

        if (result == Messages.YES) {
            isRemovingTemplate = true
            try {
                editingTemplates.removeIf { it.id == selected.id }
                if (selectedTemplateId == selected.id) {
                    selectedTemplateId = editingTemplates.firstOrNull()?.id
                }
                refreshTemplateList()
                selectTemplateInList(selectedTemplateId)
                loadSelectedTemplateContent()
            } finally {
                isRemovingTemplate = false
            }
        }
    }

    private fun canRemoveTemplate(): Boolean {
        return editingTemplates.size > 1
    }

    private fun renameSelectedTemplate() {
        val selected = templateList?.selectedValue ?: return
        val template = editingTemplates.find { it.id == selected.id } ?: return

        val newName = Messages.showInputDialog(
            project,
            "Enter new template name:",
            "Rename Template",
            null,
            template.name,
            null
        ) ?: return

        if (newName.isBlank()) {
            Messages.showErrorDialog(project, "Template name cannot be empty.", "Invalid Name")
            return
        }

        template.name = newName
        refreshTemplateList()
        selectTemplateInList(template.id)
    }

    private fun refreshTemplateList() {
        templateListModel?.clear()
        editingTemplates.forEach { template ->
            templateListModel?.addElement(TemplateListItem(template.id, template.name))
        }
    }

    private fun selectTemplateInList(id: String?) {
        if (id == null) return
        for (i in 0 until (templateListModel?.size ?: 0)) {
            if (templateListModel?.getElementAt(i)?.id == id) {
                templateList?.selectedIndex = i
                break
            }
        }
    }

    private fun saveCurrentTemplateContent() {
        if (isRemovingTemplate || isInitializing) return
        val currentId = selectedTemplateId ?: return
        val content = promptTextArea?.text ?: return
        editingTemplates.find { it.id == currentId }?.content = content
    }

    private fun loadSelectedTemplateContent() {
        val template = selectedTemplateId?.let { id -> editingTemplates.find { it.id == id } }
        promptTextArea?.text = template?.content ?: ""
        promptTextArea?.isEnabled = template != null
    }

    private fun updatePromptVisibility() {
        promptPanel?.isVisible = markdownRadioButton?.isSelected == true
    }

    override fun isModified(): Boolean {
        saveCurrentTemplateContent()
        val settings = ChronicleSettings.getInstance(project)
        val selectedFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN
        val selectedDestination = if (clipboardRadioButton?.isSelected == true) ExportDestination.CLIPBOARD else ExportDestination.FILE

        if (selectedFormat != settings.exportFormat) return true
        if (selectedDestination != settings.exportDestination) return true
        if (waveformCheckbox?.isSelected != settings.showWaveformVisualization) return true
        if (selectedTemplateId != settings.selectedTemplateId) return true

        val currentTemplates = settings.getTemplates()
        if (editingTemplates.size != currentTemplates.size) return true

        for (editing in editingTemplates) {
            val original = currentTemplates.find { it.id == editing.id } ?: return true
            if (original.name != editing.name || original.content != editing.content) return true
        }

        return false
    }

    override fun apply() {
        saveCurrentTemplateContent()
        val settings = ChronicleSettings.getInstance(project)
        settings.exportFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN
        settings.exportDestination = if (clipboardRadioButton?.isSelected == true) ExportDestination.CLIPBOARD else ExportDestination.FILE
        settings.showWaveformVisualization = waveformCheckbox?.isSelected ?: false

        settings.promptTemplates.clear()
        editingTemplates.forEach { template ->
            settings.promptTemplates.add(template.copy())
        }

        selectedTemplateId?.let { settings.selectedTemplateId = it }
    }

    override fun reset() {
        isInitializing = true
        try {
            val settings = ChronicleSettings.getInstance(project)
            when (settings.exportFormat) {
                ExportFormat.JSON -> jsonRadioButton?.isSelected = true
                ExportFormat.MARKDOWN -> markdownRadioButton?.isSelected = true
            }
            when (settings.exportDestination) {
                ExportDestination.CLIPBOARD -> clipboardRadioButton?.isSelected = true
                ExportDestination.FILE -> fileRadioButton?.isSelected = true
            }

            editingTemplates.clear()
            settings.getTemplates().forEach { template ->
                editingTemplates.add(template.copy())
            }
            selectedTemplateId = settings.selectedTemplateId

            refreshTemplateList()
            selectTemplateInList(selectedTemplateId)
            loadSelectedTemplateContent()

            waveformCheckbox?.isSelected = settings.showWaveformVisualization
            updatePromptVisibility()
        } finally {
            isInitializing = false
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
        jsonRadioButton = null
        markdownRadioButton = null
        clipboardRadioButton = null
        fileRadioButton = null
        promptTextArea = null
        promptPanel = null
        waveformCheckbox = null
        templateList = null
        templateListModel = null
        editingTemplates.clear()
        isRemovingTemplate = false
        isInitializing = false
    }

    private data class TemplateListItem(val id: String, val name: String) {
        override fun toString(): String = name
    }

    private class TemplateListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            border = JBUI.Borders.empty(4, 8)
            return component
        }
    }
}
