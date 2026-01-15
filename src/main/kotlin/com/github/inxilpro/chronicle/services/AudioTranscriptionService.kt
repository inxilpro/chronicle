package com.github.inxilpro.chronicle.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class AudioTranscriptionService(private val project: Project) : Disposable {

    fun interface StateChangeListener {
        fun onStateChanged(state: RecordingState)
    }

    enum class RecordingState {
        STOPPED,
        INITIALIZING,
        RECORDING,
        PROCESSING,
        ERROR
    }

    private val audioManager = AudioCaptureManager(
        minChunkMs = 30_000,
        maxChunkMs = 5 * 60 * 1000,
        silenceThresholdRms = 0.015f,
        silenceDurationMs = 1500
    )
    private var processor: BatchTranscriptionProcessor? = null

    private val state = AtomicReference(RecordingState.STOPPED)
    private val stateListeners = CopyOnWriteArrayList<StateChangeListener>()
    private var lastError: String? = null

    private var selectedModel: ModelDownloader.WhisperModel = ModelDownloader.WhisperModel.MEDIUM_EN

    fun initialize(
        model: ModelDownloader.WhisperModel = ModelDownloader.WhisperModel.MEDIUM_EN,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (state.get() != RecordingState.STOPPED) {
            onComplete(false)
            return
        }

        selectedModel = model
        setState(RecordingState.INITIALIZING)

        if (ModelDownloader.isModelDownloaded(model)) {
            completeInitialization(ModelDownloader.getModelFile(model), onComplete)
        } else {
            ModelDownloader.downloadModelAsync(project, model) { modelFile ->
                if (modelFile != null) {
                    completeInitialization(modelFile, onComplete)
                } else {
                    lastError = "Model download failed or was cancelled"
                    setState(RecordingState.ERROR)
                    onComplete(false)
                }
            }
        }
    }

    private fun completeInitialization(modelFile: File, onComplete: (Boolean) -> Unit) {
        try {
            processor = BatchTranscriptionProcessor(project, audioManager)
            processor?.initialize(modelFile)
            setState(RecordingState.STOPPED)
            thisLogger().info("AudioTranscriptionService initialized with model: ${modelFile.name}")
            onComplete(true)
        } catch (e: Exception) {
            lastError = "Failed to initialize Whisper: ${e.message}"
            thisLogger().error("Failed to initialize AudioTranscriptionService", e)
            setState(RecordingState.ERROR)
            onComplete(false)
        }
    }

    fun startRecording(deviceName: String? = null) {
        val currentState = state.get()
        if (currentState == RecordingState.RECORDING) {
            thisLogger().warn("Already recording")
            return
        }

        if (processor == null || !processor!!.isInitialized()) {
            initialize { success ->
                if (success) {
                    doStartRecording(deviceName)
                }
            }
            return
        }

        doStartRecording(deviceName)
    }

    private fun doStartRecording(deviceName: String?) {
        try {
            audioManager.startRecording(deviceName)
            processor?.startProcessing()
            setState(RecordingState.RECORDING)
            thisLogger().info("Started audio recording")
        } catch (e: Exception) {
            lastError = "Failed to start recording: ${e.message}"
            thisLogger().error("Failed to start recording", e)
            setState(RecordingState.ERROR)
        }
    }

    fun stopRecording() {
        if (state.get() != RecordingState.RECORDING) return

        setState(RecordingState.PROCESSING)

        try {
            audioManager.stopRecording()
            processor?.stopProcessing()
            setState(RecordingState.STOPPED)
            thisLogger().info("Stopped audio recording")
        } catch (e: Exception) {
            lastError = "Error while stopping recording: ${e.message}"
            thisLogger().error("Error while stopping recording", e)
            setState(RecordingState.STOPPED)
        }
    }

    fun isRecording(): Boolean = state.get() == RecordingState.RECORDING

    fun isInitialized(): Boolean = processor?.isInitialized() == true

    fun getState(): RecordingState = state.get()

    fun getLastError(): String? = lastError

    fun listAvailableDevices(): List<AudioCaptureManager.AudioDevice> = audioManager.listAvailableDevices()

    fun getAvailableModels(): List<ModelDownloader.WhisperModel> = ModelDownloader.WhisperModel.entries

    fun getSelectedModel(): ModelDownloader.WhisperModel = selectedModel

    fun isModelDownloaded(model: ModelDownloader.WhisperModel): Boolean = ModelDownloader.isModelDownloaded(model)

    fun addStateListener(listener: StateChangeListener) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: StateChangeListener) {
        stateListeners.remove(listener)
    }

    private fun setState(newState: RecordingState) {
        val oldState = state.getAndSet(newState)
        if (oldState != newState) {
            stateListeners.forEach { it.onStateChanged(newState) }
        }
    }

    override fun dispose() {
        if (state.get() == RecordingState.RECORDING) {
            stopRecording()
        }
        audioManager.dispose()
        processor?.dispose()
        processor = null
        stateListeners.clear()
        thisLogger().info("AudioTranscriptionService disposed")
    }

    companion object {
        fun getInstance(project: Project): AudioTranscriptionService {
            return project.getService(AudioTranscriptionService::class.java)
        }
    }
}
