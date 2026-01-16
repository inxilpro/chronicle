package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.VisibleAreaEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent as IdeaVisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class VisibleAreaTracker(
    private val project: Project,
    private val debounceMs: Long = 500
) : FileEditorManagerListener, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private val pendingJobs = ConcurrentHashMap<Editor, ScheduledFuture<*>>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val attachedListeners = ConcurrentHashMap<Editor, VisibleAreaListener>()

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        source.getEditors(file).forEach { fileEditor ->
            if (fileEditor is TextEditor) {
                attachTo(fileEditor.editor)
            }
        }
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        source.getEditors(file).forEach { fileEditor ->
            if (fileEditor is TextEditor) {
                detachFrom(fileEditor.editor)
            }
        }
    }

    fun attachTo(editor: Editor) {
        if (attachedListeners.containsKey(editor)) return

        val listener = VisibleAreaListener { _: IdeaVisibleAreaEvent ->
            pendingJobs[editor]?.cancel(false)

            pendingJobs[editor] = executor.schedule({
                ApplicationManager.getApplication().runReadAction {
                    logVisibleArea(editor)
                }
            }, debounceMs, TimeUnit.MILLISECONDS)
        }

        attachedListeners[editor] = listener
        editor.scrollingModel.addVisibleAreaListener(listener)
    }

    private fun detachFrom(editor: Editor) {
        attachedListeners.remove(editor)?.let { listener ->
            editor.scrollingModel.removeVisibleAreaListener(listener)
        }
        pendingJobs.remove(editor)?.cancel(false)
    }

    internal fun logVisibleArea(editor: Editor) {
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return

        val visibleRange = editor.calculateVisibleRange()
        if (visibleRange.isEmpty) return

        val startLine = document.getLineNumber(visibleRange.startOffset) + 1
        val endLine = document.getLineNumber(visibleRange.endOffset) + 1

        val contentDescription = "Lines $startLine-$endLine"

        transcriptService.log(VisibleAreaEvent(
            path = file.path,
            startLine = startLine,
            endLine = endLine,
            contentDescription = contentDescription
        ))
    }

    fun attachToExistingEditors() {
        FileEditorManager.getInstance(project).allEditors.forEach { fileEditor ->
            if (fileEditor is TextEditor) {
                attachTo(fileEditor.editor)
            }
        }
    }

    override fun dispose() {
        pendingJobs.values.forEach { it.cancel(true) }
        pendingJobs.clear()
        attachedListeners.clear()
        executor.shutdownNow()
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): VisibleAreaTracker {
            val tracker = VisibleAreaTracker(project)

            project.messageBus.connect(parentDisposable)
                .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, tracker)

            // Attach to any already-open editors
            tracker.attachToExistingEditors()

            Disposer.register(parentDisposable, tracker)
            return tracker
        }
    }
}
