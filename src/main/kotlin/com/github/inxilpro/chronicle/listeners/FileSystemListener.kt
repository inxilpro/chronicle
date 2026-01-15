package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.FileCreatedEvent
import com.github.inxilpro.chronicle.events.FileDeletedEvent
import com.github.inxilpro.chronicle.events.FileMovedEvent
import com.github.inxilpro.chronicle.events.FileRenamedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

class FileSystemListener(
    private val project: Project
) : BulkFileListener, Disposable {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    override fun after(events: List<VFileEvent>) {
        events.forEach { event ->
            if (!isProjectEvent(event)) return@forEach

            when (event) {
                is VFileCreateEvent -> {
                    transcriptService.log(FileCreatedEvent(path = event.path))
                }
                is VFileDeleteEvent -> {
                    transcriptService.log(FileDeletedEvent(path = event.path))
                }
                is VFilePropertyChangeEvent -> {
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        val oldPath = event.oldPath
                        val newPath = event.path
                        transcriptService.log(FileRenamedEvent(
                            oldPath = oldPath,
                            newPath = newPath
                        ))
                    }
                }
                is VFileMoveEvent -> {
                    transcriptService.log(FileMovedEvent(
                        oldPath = event.oldPath,
                        newPath = event.newPath
                    ))
                }
            }
        }
    }

    private fun isProjectEvent(event: VFileEvent): Boolean {
        val file = event.file
        if (file != null && file.isValid) {
            if (ProjectFileIndex.getInstance(project).isInContent(file)) {
                return true
            }
        }
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots
        return contentRoots.any { root -> event.path.startsWith(root.path) }
    }

    override fun dispose() {
        // No resources to clean up
    }

    companion object {
        fun register(project: Project, parentDisposable: Disposable): FileSystemListener {
            val listener = FileSystemListener(project)
            val connection = project.messageBus.connect(parentDisposable)
            connection.subscribe(VirtualFileManager.VFS_CHANGES, listener)
            Disposer.register(parentDisposable, listener)
            return listener
        }
    }
}
