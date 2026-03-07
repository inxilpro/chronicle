package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.FileClosedEvent
import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.FileSelectedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class FileActivityListener(private val project: Project) : FileEditorManagerListener {

    private val transcriptService: ActivityTranscriptService
        get() = ActivityTranscriptService.getInstance(project)

    // Track last opened file to suppress redundant file_selected events
    @Volatile
    private var lastOpenedPath: String? = null
    @Volatile
    private var lastOpenedTime: Long = 0

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        lastOpenedPath = file.path
        lastOpenedTime = System.nanoTime()
        transcriptService.log(FileOpenedEvent(path = file.path))
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        transcriptService.log(FileClosedEvent(path = file.path))
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        event.newFile?.let { file ->
            // Suppress file_selected when it immediately follows a file_opened for the same file
            val elapsed = System.nanoTime() - lastOpenedTime
            if (file.path == lastOpenedPath && elapsed < 50_000_000L) { // 50ms
                return
            }

            transcriptService.log(FileSelectedEvent(
                path = file.path,
                previousPath = event.oldFile?.path
            ))
        }
    }
}
