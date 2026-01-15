package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.RecentFileEvent
import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.github.inxilpro.chronicle.listeners.DebouncedSelectionListener
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

    private val events: MutableList<TranscriptEvent> = CopyOnWriteArrayList()
    private var sessionStart: Instant = Instant.now()
    private val debounceTimers: MutableMap<String, ScheduledFuture<*>> = mutableMapOf()

    init {
        thisLogger().info("ActivityTranscriptService initialized for project: ${project.name}")
        captureInitialState()
        registerListeners()
    }

    private fun registerListeners() {
        DebouncedSelectionListener.register(project, this)
        VisibleAreaTracker.register(project, this)
        thisLogger().info("Registered activity listeners")
    }

    override fun dispose() {
        debounceTimers.values.forEach { it.cancel(true) }
        debounceTimers.clear()
    }

    fun log(event: TranscriptEvent) {
        events.add(event)
        thisLogger().debug("Logged event: ${event.type} at ${event.timestamp}")
    }

    fun getEvents(): List<TranscriptEvent> = events.toList()

    fun getSessionStart(): Instant = sessionStart

    fun getProjectName(): String = project.name

    fun resetSession() {
        events.clear()
        sessionStart = Instant.now()
        thisLogger().info("Session reset for project: ${project.name}")
        captureInitialState()
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
