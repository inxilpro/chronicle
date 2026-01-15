package com.github.inxilpro.chronicle.toolWindow

import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

class ChronicleToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ChroniclePanel(project)
        Disposer.register(toolWindow.disposable, panel)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

class ChroniclePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val service = ActivityTranscriptService.getInstance(project)
    private val listModel = DefaultListModel<String>()
    private val eventList = JBList(listModel)
    private val startStopButton = JButton("Stop")
    private val resetButton = JButton("Reset")
    private val statusLabel = JBLabel()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private val changeListener = ActivityTranscriptService.TranscriptChangeListener {
        ApplicationManager.getApplication().invokeLater {
            refreshList()
            updateButtonState()
        }
    }

    init {
        border = JBUI.Borders.empty(8)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(startStopButton)
            add(Box.createHorizontalStrut(8))
            add(resetButton)
            add(Box.createHorizontalGlue())
            add(statusLabel)
        }

        eventList.cellRenderer = EventListCellRenderer()
        eventList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val scrollPane = JBScrollPane(eventList).apply {
            border = JBUI.Borders.empty(8, 0)
        }

        add(buttonPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        startStopButton.addActionListener {
            if (service.isLogging) {
                service.stopLogging()
            } else {
                service.startLogging()
            }
        }

        resetButton.addActionListener {
            service.resetSession()
        }

        service.addChangeListener(changeListener)
        refreshList()
        updateButtonState()
    }

    private fun refreshList() {
        listModel.clear()
        service.getEvents().forEach { event ->
            val time = timeFormatter.format(event.timestamp)
            listModel.addElement("[$time] ${event.summary()}")
        }

        if (listModel.size() > 0) {
            eventList.ensureIndexIsVisible(listModel.size() - 1)
        }
    }

    private fun updateButtonState() {
        startStopButton.text = if (service.isLogging) "Stop" else "Start"
        val eventCount = service.getEvents().size
        val status = if (service.isLogging) "Recording" else "Paused"
        statusLabel.text = "$status • $eventCount events"
    }

    override fun dispose() {
        service.removeChangeListener(changeListener)
    }

    private class EventListCellRenderer : DefaultListCellRenderer() {
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
