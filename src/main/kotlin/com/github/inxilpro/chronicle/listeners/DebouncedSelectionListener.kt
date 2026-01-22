package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.SelectionEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.SelectionEvent as IdeaSelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DebouncedSelectionListener(
    private val project: Project,
    private val debounceMs: Long = 300
) : SelectionListener, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private var pendingJob: ScheduledFuture<*>? = null
    private var pendingEventData: SelectionEvent? = null
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun selectionChanged(event: IdeaSelectionEvent) {
        val editor = event.editor
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        // Only track selections in the associated project
        if (editor.project != project) return

        pendingJob?.cancel(false)

        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            val filePath = file.path
            val startLine = document.getLineNumber(selection.selectionStart) + 1
            val endLine = document.getLineNumber(selection.selectionEnd) + 1
            val selectedText = selection.selectedText?.take(500)

            val selectionEvent = SelectionEvent(
                path = filePath,
                startLine = startLine,
                endLine = endLine,
                text = selectedText
            )
            pendingEventData = selectionEvent

            pendingJob = executor.schedule({
                pendingEventData = null
                transcriptService.log(selectionEvent)
            }, debounceMs, TimeUnit.MILLISECONDS)
        } else {
            pendingEventData = null
        }
    }

    internal fun flushPendingEvents() {
        pendingJob?.cancel(false)
        pendingJob = null
        pendingEventData?.let { event ->
            pendingEventData = null
            transcriptService.log(event)
        }
    }

    override fun dispose() {
        pendingJob?.cancel(true)
        pendingJob = null
        pendingEventData = null
        executor.shutdownNow()
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): DebouncedSelectionListener {
            val listener = DebouncedSelectionListener(project)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(listener, parentDisposable)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
