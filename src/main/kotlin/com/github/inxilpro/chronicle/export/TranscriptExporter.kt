package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.*
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.github.inxilpro.chronicle.settings.ChronicleSettings
import com.github.inxilpro.chronicle.settings.ExportFormat
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import java.time.Instant
import java.time.format.DateTimeFormatter

class TranscriptExporter(private val project: Project) {

    private val service = ActivityTranscriptService.getInstance(project)
    private val settings = ChronicleSettings.getInstance(project)

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

        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "session": {""")
        sb.appendLine("""    "start": "${DateTimeFormatter.ISO_INSTANT.format(sessionStart)}",""")
        sb.appendLine("""    "end": "${DateTimeFormatter.ISO_INSTANT.format(sessionEnd)}",""")
        sb.appendLine("""    "project": "${escapeJson(service.getProjectName())}" """)
        sb.appendLine("  },")

        val initialFiles = events.filterIsInstance<FileOpenedEvent>().filter { it.isInitial }
        val recentFiles = events.filterIsInstance<RecentFileEvent>()

        sb.appendLine("""  "initialState": {""")
        sb.appendLine("""    "openFiles": [${initialFiles.joinToString(", ") { """"${escapeJson(it.path)}"""" }}],""")
        sb.appendLine("""    "recentFiles": [${recentFiles.joinToString(", ") { """"${escapeJson(it.path)}"""" }}]""")
        sb.appendLine("  },")

        sb.appendLine("""  "events": [""")
        val regularEvents = events.filter { it !is RecentFileEvent && !(it is FileOpenedEvent && it.isInitial) }
        regularEvents.forEachIndexed { index, event ->
            sb.append("    ")
            sb.append(eventToJson(event))
            if (index < regularEvents.size - 1) sb.append(",")
            sb.appendLine()
        }
        sb.appendLine("  ]")

        sb.appendLine("}")
        return sb.toString()
    }

    private fun eventToJson(event: TranscriptEvent): String {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(event.timestamp)
        return when (event) {
            is FileOpenedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"}"""
            is FileClosedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"}"""
            is FileSelectedEvent -> {
                val prev = event.previousPath?.let { """, "previousPath": "${escapeJson(it)}"""" } ?: ""
                """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"$prev}"""
            }
            is RecentFileEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"}"""
            is SelectionEvent -> {
                val textPart = event.text?.let { """, "text": "${escapeJson(it)}"""" } ?: ""
                """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}", "startLine": ${event.startLine}, "endLine": ${event.endLine}$textPart}"""
            }
            is DocumentChangedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}", "lineCount": ${event.lineCount}}"""
            is VisibleAreaEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}", "startLine": ${event.startLine}, "endLine": ${event.endLine}, "contentDescription": "${escapeJson(event.contentDescription)}"}"""
            is FileCreatedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"}"""
            is FileDeletedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "path": "${escapeJson(event.path)}"}"""
            is FileRenamedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "oldPath": "${escapeJson(event.oldPath)}", "newPath": "${escapeJson(event.newPath)}"}"""
            is FileMovedEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "oldPath": "${escapeJson(event.oldPath)}", "newPath": "${escapeJson(event.newPath)}"}"""
            is BranchChangedEvent -> {
                val branch = event.branch?.let { """"${escapeJson(it)}"""" } ?: "null"
                """{"type": "${event.type}", "timestamp": "$timestamp", "repository": "${escapeJson(event.repository)}", "branch": $branch, "state": "${escapeJson(event.state)}"}"""
            }
            is SearchEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "query": "${escapeJson(event.query)}"}"""
            is RefactoringEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "refactoringType": "${escapeJson(event.refactoringType)}", "details": "${escapeJson(event.details)}"}"""
            is RefactoringUndoEvent -> """{"type": "${event.type}", "timestamp": "$timestamp", "refactoringType": "${escapeJson(event.refactoringType)}"}"""
            is AudioTranscriptionEvent -> {
                val speaker = event.speakerSegment?.let { """, "speakerSegment": $it""" } ?: ""
                """{"type": "${event.type}", "timestamp": "$timestamp", "transcriptionText": "${escapeJson(event.transcriptionText)}", "durationMs": ${event.durationMs}, "language": "${escapeJson(event.language)}", "confidence": ${event.confidence}$speaker}"""
            }
            is ShellCommandEvent -> {
                val wd = event.workingDirectory?.let { """, "workingDirectory": "${escapeJson(it)}"""" } ?: ""
                """{"type": "${event.type}", "timestamp": "$timestamp", "command": "${escapeJson(event.command)}", "shell": "${escapeJson(event.shell)}"$wd}"""
            }
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        fun getInstance(project: Project): TranscriptExporter {
            return TranscriptExporter(project)
        }
    }
}
