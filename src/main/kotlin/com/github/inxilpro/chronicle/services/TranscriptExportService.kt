package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.export.InstantTypeAdapter
import com.github.inxilpro.chronicle.export.SessionMetadata
import com.github.inxilpro.chronicle.export.TranscriptEventTypeAdapter
import com.github.inxilpro.chronicle.export.TranscriptExport
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.time.Instant

@Service(Service.Level.PROJECT)
class TranscriptExportService(private val project: Project) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .registerTypeAdapter(TranscriptEvent::class.java, TranscriptEventTypeAdapter())
        .setPrettyPrinting()
        .create()

    fun exportToJson(file: File) {
        file.writeText(toJson())
    }

    fun toJson(): String {
        val transcriptService = ActivityTranscriptService.getInstance(project)
        val projectRoot = transcriptService.getProjectBasePath()
        val prefix = if (projectRoot != null) "$projectRoot/" else null
        val events = transcriptService.getEvents().sortedBy { it.timestamp }

        val relativizedEvents = if (prefix != null) {
            events.map { relativizePaths(it, prefix) }
        } else {
            events
        }

        val export = TranscriptExport(
            session = SessionMetadata(
                projectName = transcriptService.getProjectName(),
                projectRoot = projectRoot,
                sessionStart = transcriptService.getSessionStart(),
                exportedAt = Instant.now(),
                eventCount = relativizedEvents.size,
                gitBranch = transcriptService.getSessionGitBranch()
            ),
            events = relativizedEvents
        )

        return gson.toJson(export)
    }

    companion object {
        fun getInstance(project: Project): TranscriptExportService {
            return project.getService(TranscriptExportService::class.java)
        }

        internal fun stripPrefix(path: String, prefix: String): String {
            return if (path.startsWith(prefix)) path.removePrefix(prefix) else path
        }

        internal fun relativizePaths(event: TranscriptEvent, prefix: String): TranscriptEvent {
            return when (event) {
                is FileOpenedEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is FileClosedEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is FileSelectedEvent -> event.copy(
                    path = stripPrefix(event.path, prefix),
                    previousPath = event.previousPath?.let { stripPrefix(it, prefix) }
                )
                is SelectionEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is DocumentChangedEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is VisibleAreaEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is FileCreatedEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is FileDeletedEvent -> event.copy(path = stripPrefix(event.path, prefix))
                is FileRenamedEvent -> event.copy(
                    oldPath = stripPrefix(event.oldPath, prefix),
                    newPath = stripPrefix(event.newPath, prefix)
                )
                is FileMovedEvent -> event.copy(
                    oldPath = stripPrefix(event.oldPath, prefix),
                    newPath = stripPrefix(event.newPath, prefix)
                )
                is BranchChangedEvent -> event
                is SearchEvent -> event
                is RefactoringEvent -> event
                is RefactoringUndoEvent -> event
                is AudioTranscriptionEvent -> event
                is ShellCommandEvent -> event
            }
        }
    }
}
