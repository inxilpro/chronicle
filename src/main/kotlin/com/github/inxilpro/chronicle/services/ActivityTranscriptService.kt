package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.RecentFileEvent
import com.github.inxilpro.chronicle.events.TranscriptEvent
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
class ActivityTranscriptService(private val project: Project) {

    private val events: MutableList<TranscriptEvent> = CopyOnWriteArrayList()
    private var sessionStart: Instant = Instant.now()
    private val debounceTimers: MutableMap<String, ScheduledFuture<*>> = mutableMapOf()

    init {
        thisLogger().info("ActivityTranscriptService initialized for project: ${project.name}")
        captureInitialState()
    }

    /**
     * Log a transcript event.
     */
    fun log(event: TranscriptEvent) {
        events.add(event)
        thisLogger().debug("Logged event: ${event.type} at ${event.timestamp}")
    }

    /**
     * Get all logged events.
     */
    fun getEvents(): List<TranscriptEvent> = events.toList()

    /**
     * Get the session start time.
     */
    fun getSessionStart(): Instant = sessionStart

    /**
     * Get the project name.
     */
    fun getProjectName(): String = project.name

    /**
     * Clear all events and reset the session.
     */
    fun resetSession() {
        events.clear()
        sessionStart = Instant.now()
        thisLogger().info("Session reset for project: ${project.name}")
        captureInitialState()
    }

    /**
     * Store a debounce timer for a specific event type.
     */
    fun setDebounceTimer(eventType: String, timer: ScheduledFuture<*>) {
        debounceTimers[eventType]?.cancel(false)
        debounceTimers[eventType] = timer
    }

    /**
     * Cancel a debounce timer for a specific event type.
     */
    fun cancelDebounceTimer(eventType: String) {
        debounceTimers[eventType]?.cancel(false)
        debounceTimers.remove(eventType)
    }

    /**
     * Capture the initial state of the project when the session starts.
     * This includes currently open files and recent files.
     */
    private fun captureInitialState() {
        val fem = FileEditorManager.getInstance(project)

        // Log currently open files
        fem.openFiles.forEach { file ->
            log(FileOpenedEvent(
                path = file.path,
                isInitial = true
            ))
        }
        thisLogger().info("Captured ${fem.openFiles.size} open files")

        // Log recent files (last 20)
        val recentFiles = EditorHistoryManager.getInstance(project)
            .fileList
            .take(20)

        recentFiles.forEach { file ->
            log(RecentFileEvent(path = file.path))
        }
        thisLogger().info("Captured ${recentFiles.size} recent files")
    }

    companion object {
        /**
         * Get the ActivityTranscriptService instance for a project.
         */
        fun getInstance(project: Project): ActivityTranscriptService {
            return project.getService(ActivityTranscriptService::class.java)
        }
    }
}
