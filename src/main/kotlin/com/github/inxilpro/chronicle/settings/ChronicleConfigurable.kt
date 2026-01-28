package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.UUID
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent

class ChronicleConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var jsonRadioButton: JBRadioButton? = null
    private var markdownRadioButton: JBRadioButton? = null
    private var promptTextArea: JBTextArea? = null
    private var promptPanel: JPanel? = null
    private var waveformCheckbox: JBCheckBox? = null

    private var templateList: JBList<PromptTemplate>? = null
    private var templateListModel: CollectionListModel<PromptTemplate>? = null
    private var templateListPanel: JPanel? = null

    // Editing copies - separate from settings
    private var editingTemplates: MutableList<PromptTemplate> = mutableListOf()
    private var editingSelectedId: String = ""

    // Guard to prevent saving during list selection changes
    private var isLoadingTemplates = false

    override fun getDisplayName(): String = "Chronicle"

    override fun createComponent(): JComponent {
        jsonRadioButton = JBRadioButton("Raw JSON")
        markdownRadioButton = JBRadioButton("Markdown prompt")

        val buttonGroup = ButtonGroup()
        buttonGroup.add(jsonRadioButton)
        buttonGroup.add(markdownRadioButton)

        val formatPanel = JPanel().apply {
            layout = java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)
            add(jsonRadioButton)
            add(javax.swing.Box.createHorizontalStrut(16))
            add(markdownRadioButton)
        }

        // Template list setup
        templateListModel = CollectionListModel()
        templateList = JBList(templateListModel!!).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = TemplateListCellRenderer()
            addListSelectionListener { e -> onTemplateSelectionChanged(e) }
        }

        val decorator = ToolbarDecorator.createDecorator(templateList!!)
            .setAddAction { addTemplate() }
            .setRemoveAction { removeTemplate() }
            .setEditAction { renameTemplate() }
            .setEditActionUpdater { templateListModel!!.size > 0 }
            .setRemoveActionUpdater { templateListModel!!.size > 1 }
            .disableUpDownActions()

        templateListPanel = JPanel(BorderLayout()).apply {
            add(JLabel("Templates:").apply {
                border = JBUI.Borders.emptyBottom(4)
            }, BorderLayout.NORTH)
            add(decorator.createPanel().apply {
                preferredSize = Dimension(200, 150)
            }, BorderLayout.CENTER)
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

        val promptContentPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JLabel("Template content (use {{SESSION_JSON}} as placeholder):").apply {
                border = JBUI.Borders.emptyBottom(4)
            }, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val resetButton = JBLabel("<html><a href='#'>Reset to default</a></html>").apply {
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            border = JBUI.Borders.emptyTop(8)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    promptTextArea?.text = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
                }
            })
        }

        val templateContentPanel = JPanel(BorderLayout()).apply {
            add(promptContentPanel, BorderLayout.CENTER)
            add(resetButton, BorderLayout.SOUTH)
        }

        promptPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(templateListPanel)
            add(templateContentPanel)
        }

        waveformCheckbox = JBCheckBox("Show audio waveform visualization (for debugging)")

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Export format:", formatPanel)
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

    private fun onTemplateSelectionChanged(e: ListSelectionEvent) {
        if (e.valueIsAdjusting || isLoadingTemplates) return

        val selectedTemplate = templateList?.selectedValue
        if (selectedTemplate != null) {
            // Save current text to previously selected template before switching
            val previousTemplate = editingTemplates.find { it.id == editingSelectedId }
            if (previousTemplate != null && editingSelectedId != selectedTemplate.id) {
                previousTemplate.content = promptTextArea?.text ?: ""
            }

            editingSelectedId = selectedTemplate.id
            promptTextArea?.text = selectedTemplate.content
        }
    }

    private fun addTemplate() {
        val name = Messages.showInputDialog(
            project,
            "Enter template name:",
            "New Template",
            null
        ) ?: return

        if (name.isBlank()) {
            Messages.showWarningDialog(project, "Template name cannot be empty.", "Invalid Name")
            return
        }

        // Save current template content before adding new one
        saveCurrentTemplateContent()

        val newTemplate = PromptTemplate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            content = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
        )
        editingTemplates.add(newTemplate)
        templateListModel?.add(newTemplate)
        templateList?.selectedIndex = templateListModel!!.size - 1
    }

    private fun removeTemplate() {
        val selectedTemplate = templateList?.selectedValue ?: return

        if (templateListModel!!.size <= 1) {
            Messages.showWarningDialog(project, "Cannot remove the last template.", "Cannot Remove")
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            "Are you sure you want to remove the template '${selectedTemplate.name}'?",
            "Confirm Remove",
            Messages.getQuestionIcon()
        )

        if (confirm != Messages.YES) return

        val currentIndex = templateList!!.selectedIndex
        val newIndex = if (currentIndex > 0) currentIndex - 1 else 0

        isLoadingTemplates = true
        try {
            editingTemplates.remove(selectedTemplate)
            templateListModel?.remove(selectedTemplate)

            // Select new template and update text area
            if (templateListModel!!.size > 0) {
                val newSelectedTemplate = templateListModel!!.getElementAt(newIndex)
                editingSelectedId = newSelectedTemplate.id
                templateList?.selectedIndex = newIndex
                promptTextArea?.text = newSelectedTemplate.content
            }
        } finally {
            isLoadingTemplates = false
        }
    }

    private fun renameTemplate() {
        val selectedTemplate = templateList?.selectedValue ?: return

        val newName = Messages.showInputDialog(
            project,
            "Enter new name:",
            "Rename Template",
            null,
            selectedTemplate.name,
            null
        ) ?: return

        if (newName.isBlank()) {
            Messages.showWarningDialog(project, "Template name cannot be empty.", "Invalid Name")
            return
        }

        selectedTemplate.name = newName.trim()
        templateList?.repaint()
    }

    private fun saveCurrentTemplateContent() {
        val currentTemplate = editingTemplates.find { it.id == editingSelectedId }
        if (currentTemplate != null) {
            currentTemplate.content = promptTextArea?.text ?: ""
        }
    }

    private fun updatePromptVisibility() {
        promptPanel?.isVisible = markdownRadioButton?.isSelected == true
    }

    override fun isModified(): Boolean {
        val settings = ChronicleSettings.getInstance(project)
        val selectedFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN

        if (selectedFormat != settings.exportFormat) return true
        if (waveformCheckbox?.isSelected != settings.showWaveformVisualization) return true

        // Save current template content to editing copy for comparison
        saveCurrentTemplateContent()

        // Compare templates
        if (editingTemplates.size != settings.promptTemplates.size) return true
        if (editingSelectedId != settings.selectedTemplateId) return true

        for (editingTemplate in editingTemplates) {
            val savedTemplate = settings.promptTemplates.find { it.id == editingTemplate.id }
            if (savedTemplate == null) return true
            if (savedTemplate.name != editingTemplate.name) return true
            if (savedTemplate.content != editingTemplate.content) return true
        }

        return false
    }

    override fun apply() {
        val settings = ChronicleSettings.getInstance(project)
        settings.exportFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN
        settings.showWaveformVisualization = waveformCheckbox?.isSelected ?: false

        // Save current template content
        saveCurrentTemplateContent()

        // Copy editing templates to settings
        settings.promptTemplates.clear()
        editingTemplates.forEach { template ->
            settings.promptTemplates.add(PromptTemplate(
                id = template.id,
                name = template.name,
                content = template.content
            ))
        }
        settings.selectedTemplateId = editingSelectedId
    }

    override fun reset() {
        val settings = ChronicleSettings.getInstance(project)

        when (settings.exportFormat) {
            ExportFormat.JSON -> jsonRadioButton?.isSelected = true
            ExportFormat.MARKDOWN -> markdownRadioButton?.isSelected = true
        }
        waveformCheckbox?.isSelected = settings.showWaveformVisualization

        // Create editing copies of templates
        isLoadingTemplates = true
        try {
            editingTemplates.clear()
            settings.promptTemplates.forEach { template ->
                editingTemplates.add(PromptTemplate(
                    id = template.id,
                    name = template.name,
                    content = template.content
                ))
            }

            // Populate template list
            templateListModel?.removeAll()
            editingTemplates.forEach { templateListModel?.add(it) }

            // Select the current template
            editingSelectedId = settings.selectedTemplateId
            val selectedIndex = editingTemplates.indexOfFirst { it.id == editingSelectedId }
            if (selectedIndex >= 0) {
                templateList?.selectedIndex = selectedIndex
                promptTextArea?.text = editingTemplates[selectedIndex].content
            } else if (editingTemplates.isNotEmpty()) {
                editingSelectedId = editingTemplates.first().id
                templateList?.selectedIndex = 0
                promptTextArea?.text = editingTemplates.first().content
            }
        } finally {
            isLoadingTemplates = false
        }

        updatePromptVisibility()
    }

    override fun disposeUIResources() {
        mainPanel = null
        jsonRadioButton = null
        markdownRadioButton = null
        promptTextArea = null
        promptPanel = null
        waveformCheckbox = null
        templateList = null
        templateListModel = null
        templateListPanel = null
        editingTemplates.clear()
        editingSelectedId = ""
    }

    private class TemplateListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is PromptTemplate) {
                text = value.name
            }
            border = JBUI.Borders.empty(4, 8)
            return this
        }
    }
}
