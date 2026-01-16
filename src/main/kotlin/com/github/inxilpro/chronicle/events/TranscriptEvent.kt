package com.github.inxilpro.chronicle.events

import com.google.gson.JsonObject
import java.time.Instant

sealed interface TranscriptEvent {
    val type: String
    val timestamp: Instant
    fun summary(): String
    fun toJson(): JsonObject
}

fun createBaseJson(event: TranscriptEvent): JsonObject {
    val json = JsonObject()
    json.addProperty("type", event.type)
    json.addProperty("timestamp", event.timestamp.toString())
    return json
}

data class FileOpenedEvent(
    val path: String,
    val isInitial: Boolean = false,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_opened"
    override fun summary() = "Opened ${path.substringAfterLast("/")}" + if (isInitial) " (initial)" else ""
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
        addProperty("isInitial", isInitial)
    }
}

data class FileClosedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_closed"
    override fun summary() = "Closed ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
    }
}

data class FileSelectedEvent(
    val path: String,
    val previousPath: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_selected"
    override fun summary() = "Selected ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
        addProperty("previousPath", previousPath)
    }
}

data class RecentFileEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "recent_file"
    override fun summary() = "Recent: ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
    }
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
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
        addProperty("startLine", startLine)
        addProperty("endLine", endLine)
        addProperty("text", text)
    }
}

data class DocumentChangedEvent(
    val path: String,
    val lineCount: Int,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "document_changed"
    override fun summary() = "Modified ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
        addProperty("lineCount", lineCount)
    }
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
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
        addProperty("startLine", startLine)
        addProperty("endLine", endLine)
        addProperty("contentDescription", contentDescription)
    }
}

data class FileCreatedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_created"
    override fun summary() = "Created ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
    }
}

data class FileDeletedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_deleted"
    override fun summary() = "Deleted ${path.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("path", path)
    }
}

data class FileRenamedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_renamed"
    override fun summary() = "Renamed ${oldPath.substringAfterLast("/")} → ${newPath.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("oldPath", oldPath)
        addProperty("newPath", newPath)
    }
}

data class FileMovedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_moved"
    override fun summary() = "Moved ${oldPath.substringAfterLast("/")} → ${newPath.substringAfterLast("/")}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("oldPath", oldPath)
        addProperty("newPath", newPath)
    }
}

data class BranchChangedEvent(
    val repository: String,
    val branch: String?,
    val state: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "branch_changed"
    override fun summary() = "Branch: ${branch ?: "detached"}"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("repository", repository)
        addProperty("branch", branch)
        addProperty("state", state)
    }
}

data class SearchEvent(
    val query: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "search"
    override fun summary() = "Search: $query"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("query", query)
    }
}

data class RefactoringEvent(
    val refactoringType: String,
    val details: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring"
    override fun summary() = "Refactoring: $refactoringType"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("refactoringType", refactoringType)
        addProperty("details", details)
    }
}

data class RefactoringUndoEvent(
    val refactoringType: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring_undo"
    override fun summary() = "Undo: $refactoringType"
    override fun toJson() = createBaseJson(this).apply {
        addProperty("refactoringType", refactoringType)
    }
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
        val preview = transcriptionText.take(100)
        val truncated = if (transcriptionText.length > 100) "..." else ""
        return "\"$preview$truncated\""
    }

    override fun toJson() = createBaseJson(this).apply {
        addProperty("transcriptionText", transcriptionText)
        addProperty("durationMs", durationMs)
        addProperty("language", language)
        addProperty("confidence", confidence)
        addProperty("speakerSegment", speakerSegment)
    }
}
