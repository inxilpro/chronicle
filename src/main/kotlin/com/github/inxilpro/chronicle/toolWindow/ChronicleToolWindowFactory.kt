package com.github.inxilpro.chronicle.toolWindow

import com.github.inxilpro.chronicle.export.TranscriptExporter
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.github.inxilpro.chronicle.services.AudioTranscriptionService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
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

    companion object {
        private const val MAX_DISPLAYED_EVENTS = 1000
        private const val REFRESH_DEBOUNCE_MS = 100
        private const val DEFAULT_DEVICE = "Default Microphone"
    }

    private val service = ActivityTranscriptService.getInstance(project)
    private val audioService = AudioTranscriptionService.getInstance(project)
    private val listModel = CollectionListModel<String>()
    private val eventList = JBList(listModel)
    private val startStopButton = JButton("Stop")
    private val resetButton = JButton("Reset")
    private val exportButton = JButton("Export")
    private val statusLabel = JBLabel()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var refreshPending = false

    private val audioToggleButton = JButton("Disable Audio")
    private val audioDeviceCombo = JComboBox<String>()
    private val audioStatusLabel = JBLabel()
    private var audioEnabled = true

    private val changeListener = ActivityTranscriptService.TranscriptChangeListener {
        scheduleRefresh()
    }

    private val audioStateListener = AudioTranscriptionService.StateChangeListener { state ->
        ApplicationManager.getApplication().invokeLater {
            updateAudioState(state)
        }
    }

    init {
        border = JBUI.Borders.empty(8)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(startStopButton)
            add(Box.createHorizontalStrut(8))
            add(resetButton)
            add(Box.createHorizontalStrut(8))
            add(exportButton)
            add(Box.createHorizontalGlue())
            add(statusLabel)
        }

        val audioPanel = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4)).apply {
            add(audioDeviceCombo)
            add(audioToggleButton)
            add(audioStatusLabel)
        }

        val controlsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(buttonPanel)
            add(Box.createVerticalStrut(4))
            add(audioPanel)
        }

        eventList.fixedCellHeight = JBUI.scale(24)
        eventList.cellRenderer = EventListCellRenderer()
        eventList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        val scrollPane = JBScrollPane(eventList).apply {
            border = JBUI.Borders.empty(8, 0)
        }

        add(controlsPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        startStopButton.addActionListener {
            if (service.isLogging) {
                service.stopLogging()
                if (audioEnabled) {
                    audioService.stopRecording()
                }
            } else {
                service.startLogging()
                if (audioEnabled && !audioService.isRecording()) {
                    startAudioRecording()
                }
            }
        }

        resetButton.addActionListener {
            service.resetSession()
        }

        exportButton.addActionListener {
            TranscriptExporter.getInstance(project).export()
        }

        audioToggleButton.addActionListener {
            audioEnabled = !audioEnabled
            updateAudioToggleButton()
            updateAudioStatusLabel()

            if (audioEnabled && service.isLogging) {
                startAudioRecording()
            } else if (!audioEnabled && audioService.isRecording()) {
                audioService.stopRecording()
            }
        }

        service.addChangeListener(changeListener)
        audioService.addStateListener(audioStateListener)

        refreshAudioDevices()
        refreshList()
        updateButtonState()
        updateAudioToggleButton()
        updateAudioState(audioService.getState())
    }

    private fun startAudioRecording() {
        val selectedDevice = audioDeviceCombo.selectedItem as? String
        val deviceName = if (selectedDevice == DEFAULT_DEVICE) null else selectedDevice
        audioService.startRecording(deviceName)
    }

    private fun refreshAudioDevices() {
        audioDeviceCombo.removeAllItems()
        audioDeviceCombo.addItem(DEFAULT_DEVICE)
        audioService.listAvailableDevices().forEach { device ->
            audioDeviceCombo.addItem(device.name)
        }
    }

    private fun updateAudioToggleButton() {
        audioToggleButton.text = if (audioEnabled) "Disable Audio" else "Enable Audio"
        audioDeviceCombo.isEnabled = !audioEnabled || !service.isLogging
    }

    private fun updateAudioStatusLabel() {
        val state = audioService.getState()
        audioStatusLabel.text = when (state) {
            AudioTranscriptionService.RecordingState.STOPPED -> ""
            AudioTranscriptionService.RecordingState.INITIALIZING -> "Loading model..."
            AudioTranscriptionService.RecordingState.RECORDING -> ""
            AudioTranscriptionService.RecordingState.PROCESSING -> "Processing..."
            AudioTranscriptionService.RecordingState.ERROR -> "Error: ${audioService.getLastError() ?: "Unknown"}"
        }
    }

    private fun updateAudioState(state: AudioTranscriptionService.RecordingState) {
        when (state) {
            AudioTranscriptionService.RecordingState.STOPPED -> {
                audioStatusLabel.text = ""
                audioToggleButton.isEnabled = true
            }
            AudioTranscriptionService.RecordingState.INITIALIZING -> {
                audioStatusLabel.text = "Loading model..."
                audioToggleButton.isEnabled = false
            }
            AudioTranscriptionService.RecordingState.RECORDING -> {
                audioStatusLabel.text = ""
                audioToggleButton.isEnabled = true
            }
            AudioTranscriptionService.RecordingState.PROCESSING -> {
                audioStatusLabel.text = "Processing..."
                audioToggleButton.isEnabled = false
            }
            AudioTranscriptionService.RecordingState.ERROR -> {
                val error = audioService.getLastError() ?: "Unknown error"
                audioStatusLabel.text = "Error: $error"
                audioToggleButton.isEnabled = true
            }
        }
        updateAudioToggleButton()
    }

    private fun scheduleRefresh() {
        if (refreshPending) return
        refreshPending = true
        refreshAlarm.addRequest({
            refreshPending = false
            ApplicationManager.getApplication().invokeLater {
                refreshList()
                updateButtonState()
            }
        }, REFRESH_DEBOUNCE_MS)
    }

    private fun refreshList() {
        val events = service.getEvents().sortedBy { it.timestamp }
        val displayedEvents = events.takeLast(MAX_DISPLAYED_EVENTS)
        val items = displayedEvents.map { event ->
            val time = timeFormatter.format(event.timestamp)
            "[$time] ${event.summary()}"
        }
        listModel.replaceAll(items)

        if (listModel.size > 0) {
            eventList.ensureIndexIsVisible(listModel.size - 1)
        }
    }

    private fun updateButtonState() {
        startStopButton.text = if (service.isLogging) "Stop" else "Start"
        val totalEvents = service.getEvents().size
        val truncatedNote = if (totalEvents > MAX_DISPLAYED_EVENTS) " (showing last $MAX_DISPLAYED_EVENTS)" else ""
        statusLabel.text = "$totalEvents events$truncatedNote"
    }

    override fun dispose() {
        service.removeChangeListener(changeListener)
        audioService.removeStateListener(audioStateListener)
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

private class WrapLayout(align: Int, hgap: Int, vgap: Int) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension {
        return layoutSize(target, true)
    }

    override fun minimumLayoutSize(target: Container): Dimension {
        val minimum = layoutSize(target, false)
        minimum.width -= (hgap + 1)
        return minimum
    }

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            val targetWidth = if (target.size.width > 0) target.size.width else Int.MAX_VALUE
            val insets = target.insets
            val maxWidth = targetWidth - insets.left - insets.right - hgap * 2

            val dim = Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0

            for (i in 0 until target.componentCount) {
                val component = target.getComponent(i)
                if (component.isVisible) {
                    val d = if (preferred) component.preferredSize else component.minimumSize

                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        dim.width = maxOf(dim.width, rowWidth)
                        dim.height += rowHeight + vgap
                        rowWidth = 0
                        rowHeight = 0
                    }

                    if (rowWidth != 0) {
                        rowWidth += hgap
                    }
                    rowWidth += d.width
                    rowHeight = maxOf(rowHeight, d.height)
                }
            }

            dim.width = maxOf(dim.width, rowWidth)
            dim.height += rowHeight

            dim.width += insets.left + insets.right + hgap * 2
            dim.height += insets.top + insets.bottom + vgap * 2

            return dim
        }
    }
}
