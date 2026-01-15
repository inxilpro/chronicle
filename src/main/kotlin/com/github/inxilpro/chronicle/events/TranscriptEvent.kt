package com.github.inxilpro.chronicle.events

import java.time.Instant

/**
 * Base interface for all transcript events.
 */
sealed interface TranscriptEvent {
    val type: String
    val timestamp: Instant
}

/**
 * Event logged when a file is opened.
 */
data class FileOpenedEvent(
    val path: String,
    val isInitial: Boolean = false,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_opened"
}

/**
 * Event logged when a file is closed.
 */
data class FileClosedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_closed"
}

/**
 * Event logged when file selection changes in the editor.
 */
data class FileSelectedEvent(
    val path: String,
    val previousPath: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_selected"
}

/**
 * Event logged for recent files captured at session start.
 */
data class RecentFileEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "recent_file"
}

/**
 * Event logged when text is selected in the editor.
 */
data class SelectionEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val text: String? = null,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "selection"
}

/**
 * Event logged when a document is modified.
 */
data class DocumentChangedEvent(
    val path: String,
    val lineCount: Int,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "document_changed"
}

/**
 * Event logged when visible area changes in the editor.
 */
data class VisibleAreaEvent(
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val summary: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "visible_area"
}

/**
 * Event logged when a file is created.
 */
data class FileCreatedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_created"
}

/**
 * Event logged when a file is deleted.
 */
data class FileDeletedEvent(
    val path: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_deleted"
}

/**
 * Event logged when a file is renamed.
 */
data class FileRenamedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_renamed"
}

/**
 * Event logged when a file is moved.
 */
data class FileMovedEvent(
    val oldPath: String,
    val newPath: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "file_moved"
}

/**
 * Event logged when Git branch changes.
 */
data class BranchChangedEvent(
    val repository: String,
    val branch: String?,
    val state: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "branch_changed"
}

/**
 * Event logged when a search is performed.
 */
data class SearchEvent(
    val query: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "search"
}

/**
 * Event logged when a refactoring is completed.
 */
data class RefactoringEvent(
    val refactoringType: String,
    val details: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring"
}

/**
 * Event logged when a refactoring is undone.
 */
data class RefactoringUndoEvent(
    val refactoringType: String,
    override val timestamp: Instant = Instant.now()
) : TranscriptEvent {
    override val type: String = "refactoring_undo"
}
