package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.DocumentChangedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class DocumentChangeListener(
    private val project: Project,
    private val debounceMs: Long = 500
) : BulkAwareDocumentListener.Simple, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    private val pendingJobs = ConcurrentHashMap<Document, ScheduledFuture<*>>()
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun afterDocumentChange(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!isProjectFile(file)) return

        pendingJobs[document]?.cancel(false)

        val filePath = file.path
        pendingJobs[document] = executor.schedule({
            transcriptService.log(DocumentChangedEvent(
                path = filePath,
                lineCount = document.lineCount
            ))
        }, debounceMs, TimeUnit.MILLISECONDS)
    }

    private fun isProjectFile(file: VirtualFile): Boolean {
        if (ProjectFileIndex.getInstance(project).isInContent(file)) {
            return true
        }
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots
        return contentRoots.any { root -> file.path.startsWith(root.path) }
    }

    override fun dispose() {
        pendingJobs.values.forEach { it.cancel(true) }
        pendingJobs.clear()
        executor.shutdownNow()
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): DocumentChangeListener {
            val listener = DocumentChangeListener(project)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parentDisposable)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
