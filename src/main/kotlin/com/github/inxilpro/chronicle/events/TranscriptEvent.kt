package com.github.inxilpro.chronicle.events

import java.time.Instant

sealed interface TranscriptEvent {
    val type: String
    val timestamp: Instant
    fun summary(): String
}

data class FileOpenedEvent(
    val path: String,
    val isInitial: Boolean = false,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_opened"
    override fun summary() = "Opened ${path.substringAfterLast("/")}" + if (isInitial) " (initial)" else ""
}

data class FileClosedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_closed"
    override fun summary() = "Closed ${path.substringAfterLast("/")}"
}

data class FileSelectedEvent(
    val path: String,
    val previousPath: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_selected"
    override fun summary() = "Selected ${path.substringAfterLast("/")}"
}

data class RecentFileEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "recent_file"
    override fun summary() = "Recent: ${path.substringAfterLast("/")}"
}

data class SelectionEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val text: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "selection"
    override fun summary(): String {
        val file = path.substringAfterLast("/")
        val lines = if (startLine == endLine) "line $startLine" else "lines $startLine-$endLine"
        return "Selected $lines in $file"
    }
}

data class DocumentChangedEvent(
    val path: String,
    val lineCount: Int,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "document_changed"
    override fun summary() = "Modified ${path.substringAfterLast("/")}"
}

data class VisibleAreaEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val contentDescription: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "visible_area"
    override fun summary() = "Viewing ${path.substringAfterLast("/")}:$startLine-$endLine"
}

data class FileCreatedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_created"
    override fun summary() = "Created ${path.substringAfterLast("/")}"
}

data class FileDeletedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_deleted"
    override fun summary() = "Deleted ${path.substringAfterLast("/")}"
}

data class FileRenamedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_renamed"
    override fun summary() = "Renamed ${oldPath.substringAfterLast("/")} → ${newPath.substringAfterLast("/")}"
}

data class FileMovedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_moved"
    override fun summary() = "Moved ${oldPath.substringAfterLast("/")} → ${newPath.substringAfterLast("/")}"
}

data class BranchChangedEvent(
    val repository: String,
    val branch: String?,
    val state: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "branch_changed"
    override fun summary() = "Branch: ${branch ?: "detached"}"
}

data class SearchEvent(
    val query: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "search"
    override fun summary() = "Search: $query"
}

data class RefactoringEvent(
    val refactoringType: String,
    val details: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring"
    override fun summary() = "Refactoring: $refactoringType"
}

data class RefactoringUndoEvent(
    val refactoringType: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring_undo"
    override fun summary() = "Undo: $refactoringType"
}

data class AudioTranscriptionEvent(
    val transcriptionText: String,
    val durationMs: Long,
    val language: String,
    val confidence: Float,
    val speakerSegment: Int? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "audio_transcription"

    override fun summary(): String {
        val speakerPrefix = speakerSegment?.let { "Speaker $it: " } ?: ""
        val preview = transcriptionText.take(100)
        val truncated = if (transcriptionText.length > 100) "..." else ""
        return "$speakerPrefix$preview$truncated (${durationMs / 1000}s, ${(confidence * 100).toInt()}%)"
    }
}

data class ShellCommandEvent(
    val command: String,
    val shell: String,
    val workingDirectory: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "shell_command"

    override fun summary(): String {
        val preview = command.take(80)
        val truncated = if (command.length > 80) "..." else ""
        return "[$shell] $preview$truncated"
    }
}
