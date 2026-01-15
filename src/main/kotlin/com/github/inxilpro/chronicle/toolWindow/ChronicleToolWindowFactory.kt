package com.github.inxilpro.chronicle.toolWindow

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
    private val statusLabel = JBLabel()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var refreshPending = false

    private val audioRecordButton = JButton("Start Audio")
    private val audioDeviceCombo = JComboBox<String>()
    private val audioStatusLabel = JBLabel()

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
            add(Box.createHorizontalGlue())
            add(statusLabel)
        }

        val audioPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Audio:"))
            add(audioDeviceCombo)
            add(audioRecordButton)
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
            } else {
                service.startLogging()
            }
        }

        resetButton.addActionListener {
            service.resetSession()
        }

        audioRecordButton.addActionListener {
            when (audioService.getState()) {
                AudioTranscriptionService.RecordingState.RECORDING -> {
                    audioService.stopRecording()
                }
                AudioTranscriptionService.RecordingState.STOPPED,
                AudioTranscriptionService.RecordingState.ERROR -> {
                    val selectedDevice = audioDeviceCombo.selectedItem as? String
                    val deviceName = if (selectedDevice == DEFAULT_DEVICE) null else selectedDevice
                    audioService.startRecording(deviceName)
                }
                else -> {}
            }
        }

        service.addChangeListener(changeListener)
        audioService.addStateListener(audioStateListener)

        refreshAudioDevices()
        refreshList()
        updateButtonState()
        updateAudioState(audioService.getState())
    }

    private fun refreshAudioDevices() {
        audioDeviceCombo.removeAllItems()
        audioDeviceCombo.addItem(DEFAULT_DEVICE)
        audioService.listAvailableDevices().forEach { device ->
            audioDeviceCombo.addItem(device.name)
        }
    }

    private fun updateAudioState(state: AudioTranscriptionService.RecordingState) {
        when (state) {
            AudioTranscriptionService.RecordingState.STOPPED -> {
                audioRecordButton.text = "Start Audio"
                audioRecordButton.isEnabled = true
                audioDeviceCombo.isEnabled = true
                audioStatusLabel.text = ""
            }
            AudioTranscriptionService.RecordingState.INITIALIZING -> {
                audioRecordButton.text = "Initializing..."
                audioRecordButton.isEnabled = false
                audioDeviceCombo.isEnabled = false
                audioStatusLabel.text = "Loading model..."
            }
            AudioTranscriptionService.RecordingState.RECORDING -> {
                audioRecordButton.text = "Stop Audio"
                audioRecordButton.isEnabled = true
                audioDeviceCombo.isEnabled = false
                audioStatusLabel.text = "Recording"
            }
            AudioTranscriptionService.RecordingState.PROCESSING -> {
                audioRecordButton.text = "Processing..."
                audioRecordButton.isEnabled = false
                audioDeviceCombo.isEnabled = false
                audioStatusLabel.text = "Processing remaining audio..."
            }
            AudioTranscriptionService.RecordingState.ERROR -> {
                audioRecordButton.text = "Start Audio"
                audioRecordButton.isEnabled = true
                audioDeviceCombo.isEnabled = true
                val error = audioService.getLastError() ?: "Unknown error"
                audioStatusLabel.text = "Error: $error"
            }
        }
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
        val events = service.getEvents()
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
        val status = if (service.isLogging) "Recording" else "Paused"
        val truncatedNote = if (totalEvents > MAX_DISPLAYED_EVENTS) " (showing last $MAX_DISPLAYED_EVENTS)" else ""
        statusLabel.text = "$status • $totalEvents events$truncatedNote"
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
