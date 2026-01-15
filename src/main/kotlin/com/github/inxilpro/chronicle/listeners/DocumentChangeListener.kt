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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class DocumentChangeListener(
    private val project: Project
) : BulkAwareDocumentListener.Simple, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    override fun afterDocumentChange(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!isProjectFile(file)) return

        transcriptService.log(DocumentChangedEvent(
            path = file.path,
            lineCount = document.lineCount
        ))
    }

    private fun isProjectFile(file: VirtualFile): Boolean {
        if (ProjectFileIndex.getInstance(project).isInContent(file)) {
            return true
        }
        val projectBasePath = project.basePath ?: return false
        return file.path.startsWith(projectBasePath)
    }

    override fun dispose() {
        // No resources to clean up
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
