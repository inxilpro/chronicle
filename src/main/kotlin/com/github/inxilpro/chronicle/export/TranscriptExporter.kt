package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.github.inxilpro.chronicle.settings.ExportFormat
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import java.time.Instant
import java.time.format.DateTimeFormatter

class TranscriptExporter(private val project: Project) {

    private val service = ActivityTranscriptService.getInstance(project)
    private val settings = ChronicleSettings.getInstance(project)

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant::class.java, InstantAdapter())
        .create()

    fun export() {
        val extension = when (settings.exportFormat) {
            ExportFormat.JSON -> "json"
            ExportFormat.MARKDOWN -> "md"
        }

        val descriptor = FileSaverDescriptor(
            "Export Chronicle Session",
            "Choose where to save the session export",
            extension
        )

        val defaultFileName = "chronicle-session-${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")}"

        val saveDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper: VirtualFileWrapper? = saveDialog.save(defaultFileName)

        wrapper?.let { fileWrapper ->
            val content = generateExportContent()
            fileWrapper.file.writeText(content)
        }
    }

    fun generateExportContent(): String {
        val json = generateJson()
        return when (settings.exportFormat) {
            ExportFormat.JSON -> json
            ExportFormat.MARKDOWN -> settings.markdownPromptTemplate.replace("{{SESSION_JSON}}", json)
        }
    }

    fun generateJson(): String {
        val events = service.getEvents().sortedBy { it.timestamp }
        val sessionStart = service.getSessionStart()
        val sessionEnd = events.lastOrNull()?.timestamp ?: Instant.now()

        val initialFiles = events.filterIsInstance<FileOpenedEvent>().filter { it.isInitial }
        val recentFiles = events.filterIsInstance<RecentFileEvent>()
        val regularEvents = events.filter { it !is RecentFileEvent && !(it is FileOpenedEvent && it.isInitial) }

        val exportData = SessionExport(
            session = SessionInfo(
                start = sessionStart,
                end = sessionEnd,
                project = service.getProjectName()
            ),
            initialState = InitialState(
                openFiles = initialFiles.map { it.path },
                recentFiles = recentFiles.map { it.path }
            ),
            events = regularEvents.map { it.toExportEvent() }
        )

        return gson.toJson(exportData)
    }

    private fun TranscriptEvent.toExportEvent(): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "type" to type,
            "timestamp" to timestamp
        )

        when (this) {
            is FileOpenedEvent -> base["path"] = path
            is FileClosedEvent -> base["path"] = path
            is FileSelectedEvent -> {
                base["path"] = path
                previousPath?.let { base["previousPath"] = it }
            }
            is RecentFileEvent -> base["path"] = path
            is SelectionEvent -> {
                base["path"] = path
                base["startLine"] = startLine
                base["endLine"] = endLine
                text?.let { base["text"] = it }
            }
            is DocumentChangedEvent -> {
                base["path"] = path
                base["lineCount"] = lineCount
            }
            is VisibleAreaEvent -> {
                base["path"] = path
                base["startLine"] = startLine
                base["endLine"] = endLine
                base["contentDescription"] = contentDescription
            }
            is FileCreatedEvent -> base["path"] = path
            is FileDeletedEvent -> base["path"] = path
            is FileRenamedEvent -> {
                base["oldPath"] = oldPath
                base["newPath"] = newPath
            }
            is FileMovedEvent -> {
                base["oldPath"] = oldPath
                base["newPath"] = newPath
            }
            is BranchChangedEvent -> {
                base["repository"] = repository
                base["branch"] = branch
                base["state"] = state
            }
            is SearchEvent -> base["query"] = query
            is RefactoringEvent -> {
                base["refactoringType"] = refactoringType
                base["details"] = details
            }
            is RefactoringUndoEvent -> base["refactoringType"] = refactoringType
            is AudioTranscriptionEvent -> {
                base["transcriptionText"] = transcriptionText
                base["durationMs"] = durationMs
                base["language"] = language
                base["confidence"] = confidence
                speakerSegment?.let { base["speakerSegment"] = it }
            }
            is ShellCommandEvent -> {
                base["command"] = command
                base["shell"] = shell
                workingDirectory?.let { base["workingDirectory"] = it }
            }
        }

        return base
    }

    companion object {
        fun getInstance(project: Project): TranscriptExporter {
            return TranscriptExporter(project)
        }
    }
}

private data class SessionExport(
    val session: SessionInfo,
    val initialState: InitialState,
    val events: List<Map<String, Any?>>
)

private data class SessionInfo(
    val start: Instant,
    val end: Instant,
    val project: String
)

private data class InitialState(
    val openFiles: List<String>,
    val recentFiles: List<String>
)

private class InstantAdapter : TypeAdapter<Instant>() {
    override fun write(out: JsonWriter, value: Instant?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(DateTimeFormatter.ISO_INSTANT.format(value))
        }
    }

    override fun read(`in`: JsonReader): Instant? {
        val value = `in`.nextString()
        return Instant.parse(value)
    }
}
