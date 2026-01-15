package com.github.inxilpro.chronicle.events

import java.time.Instant

sealed interface TranscriptEvent {
    val type: String
    val timestamp: Instant
}

data class FileOpenedEvent(
    val path: String,
    val isInitial: Boolean = false,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_opened"
}

data class FileClosedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_closed"
}

data class FileSelectedEvent(
    val path: String,
    val previousPath: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_selected"
}

data class RecentFileEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "recent_file"
}

data class SelectionEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val text: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "selection"
}

data class DocumentChangedEvent(
    val path: String,
    val lineCount: Int,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "document_changed"
}

data class VisibleAreaEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val summary: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "visible_area"
}

data class FileCreatedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_created"
}

data class FileDeletedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_deleted"
}

data class FileRenamedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_renamed"
}

data class FileMovedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_moved"
}

data class BranchChangedEvent(
    val repository: String,
    val branch: String?,
    val state: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "branch_changed"
}

data class SearchEvent(
    val query: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "search"
}

data class RefactoringEvent(
    val refactoringType: String,
    val details: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring"
}

data class RefactoringUndoEvent(
    val refactoringType: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring_undo"
}
