package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.RecentFileEvent
import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.github.inxilpro.chronicle.listeners.DebouncedSelectionListener
import com.github.inxilpro.chronicle.listeners.DocumentChangeListener
import com.github.inxilpro.chronicle.listeners.FileSystemListener
import com.github.inxilpro.chronicle.listeners.SearchEverywhereTracker
import com.github.inxilpro.chronicle.listeners.VisibleAreaTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture

/**
 * Central service that coordinates all listeners, aggregates events into a timestamped log,
 * and provides export functionality for LLM consumption.
 */
@Service(Service.Level.PROJECT)
class ActivityTranscriptService(private val project: Project) : Disposable {

    fun interface TranscriptChangeListener {
        fun onTranscriptChanged()
    }

    private val events: MutableList<TranscriptEvent> = CopyOnWriteArrayList()
    private var sessionStart: Instant = Instant.now()
    private val debounceTimers: MutableMap<String, ScheduledFuture<*>> = mutableMapOf()
    private val changeListeners: MutableList<TranscriptChangeListener> = CopyOnWriteArrayList()

    var isLogging: Boolean = false
        private set

    init {
        thisLogger().info("ActivityTranscriptService initialized for project: ${project.name}")
        registerListeners()
    }

    private fun registerListeners() {
        DebouncedSelectionListener.register(project, this)
        DocumentChangeListener.register(project, this)
        FileSystemListener.register(project, this)
        VisibleAreaTracker.register(project, this)
        SearchEverywhereTracker.register(project, this)
        thisLogger().info("Registered activity listeners")
    }

    override fun dispose() {
        debounceTimers.values.forEach { it.cancel(true) }
        debounceTimers.clear()
    }

    fun log(event: TranscriptEvent, allowBackfill: Boolean = false) {
        if (!isLogging && !allowBackfill) return
        events.add(event)
        thisLogger().debug("Logged event: ${event.type} at ${event.timestamp}")
        notifyListeners()
    }

    fun getEvents(): List<TranscriptEvent> = events.toList()

    fun getSessionStart(): Instant = sessionStart

    fun getProjectName(): String = project.name

    fun resetSession() {
        events.clear()
        sessionStart = Instant.now()
        thisLogger().info("Session reset for project: ${project.name}")
        captureInitialState()
        notifyListeners()
    }

    fun startLogging() {
        val shouldCaptureInitialState = events.isEmpty()
        isLogging = true
        if (shouldCaptureInitialState) {
            captureInitialState()
        }
        thisLogger().info("Logging started for project: ${project.name}")
        notifyListeners()
    }

    fun stopLogging() {
        isLogging = false
        thisLogger().info("Logging stopped for project: ${project.name}")
        notifyListeners()
    }

    fun addChangeListener(listener: TranscriptChangeListener) {
        changeListeners.add(listener)
    }

    fun removeChangeListener(listener: TranscriptChangeListener) {
        changeListeners.remove(listener)
    }

    private fun notifyListeners() {
        changeListeners.forEach { it.onTranscriptChanged() }
    }

    fun setDebounceTimer(eventType: String, timer: ScheduledFuture<*>) {
        debounceTimers[eventType]?.cancel(false)
        debounceTimers[eventType] = timer
    }

    fun cancelDebounceTimer(eventType: String) {
        debounceTimers[eventType]?.cancel(false)
        debounceTimers.remove(eventType)
    }

    private fun captureInitialState() {
        val fem = FileEditorManager.getInstance(project)

        fem.openFiles.forEach { file ->
            log(FileOpenedEvent(
                path = file.path,
                isInitial = true
            ))
        }
        thisLogger().info("Captured ${fem.openFiles.size} open files")

        val recentFiles = EditorHistoryManager.getInstance(project)
            .fileList
            .take(20)

        recentFiles.forEach { file ->
            log(RecentFileEvent(path = file.path))
        }
        thisLogger().info("Captured ${recentFiles.size} recent files")
    }

    companion object {
        fun getInstance(project: Project): ActivityTranscriptService {
            return project.getService(ActivityTranscriptService::class.java)
        }
    }
}
