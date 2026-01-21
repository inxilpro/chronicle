package com.github.inxilpro.chronicle.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class ChronicleConfigurable(private val project: Project) : Configurable {

    private var mainPanel: JPanel? = null
    private var jsonRadioButton: JBRadioButton? = null
    private var markdownRadioButton: JBRadioButton? = null
    private var promptTextArea: JBTextArea? = null
    private var promptPanel: JPanel? = null
    private var waveformCheckbox: JBCheckBox? = null

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

        promptTextArea = JBTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            rows = 20
            font = JBUI.Fonts.create("Monospaced", 12)
        }

        val scrollPane = JBScrollPane(promptTextArea).apply {
            preferredSize = Dimension(600, 400)
        }

        promptPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            add(JLabel("Prompt template (use {{SESSION_JSON}} as placeholder):").apply {
                border = JBUI.Borders.emptyBottom(4)
            }, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        val resetButton = com.intellij.ui.components.JBLabel("<html><a href='#'>Reset to default</a></html>").apply {
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            border = JBUI.Borders.emptyTop(8)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    promptTextArea?.text = ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
                }
            })
        }

        val promptWithReset = JPanel(BorderLayout()).apply {
            add(promptPanel, BorderLayout.CENTER)
            add(resetButton, BorderLayout.SOUTH)
        }

        waveformCheckbox = JBCheckBox("Show audio waveform visualization (for debugging)")

        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Export format:", formatPanel)
            .addComponent(promptWithReset)
            .addSeparator()
            .addComponent(waveformCheckbox!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        jsonRadioButton?.addActionListener { updatePromptVisibility() }
        markdownRadioButton?.addActionListener { updatePromptVisibility() }

        reset()

        return mainPanel!!
    }

    private fun updatePromptVisibility() {
        promptPanel?.isVisible = markdownRadioButton?.isSelected == true
    }

    override fun isModified(): Boolean {
        val settings = ChronicleSettings.getInstance(project)
        val selectedFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN
        return selectedFormat != settings.exportFormat ||
                promptTextArea?.text != settings.markdownPromptTemplate ||
                waveformCheckbox?.isSelected != settings.showWaveformVisualization
    }

    override fun apply() {
        val settings = ChronicleSettings.getInstance(project)
        settings.exportFormat = if (jsonRadioButton?.isSelected == true) ExportFormat.JSON else ExportFormat.MARKDOWN
        settings.markdownPromptTemplate = promptTextArea?.text ?: ChronicleSettings.DEFAULT_MARKDOWN_PROMPT
        settings.showWaveformVisualization = waveformCheckbox?.isSelected ?: false
    }

    override fun reset() {
        val settings = ChronicleSettings.getInstance(project)
        when (settings.exportFormat) {
            ExportFormat.JSON -> jsonRadioButton?.isSelected = true
            ExportFormat.MARKDOWN -> markdownRadioButton?.isSelected = true
        }
        promptTextArea?.text = settings.markdownPromptTemplate
        waveformCheckbox?.isSelected = settings.showWaveformVisualization
        updatePromptVisibility()
    }

    override fun disposeUIResources() {
        mainPanel = null
        jsonRadioButton = null
        markdownRadioButton = null
        promptTextArea = null
        promptPanel = null
        waveformCheckbox = null
    }
}
