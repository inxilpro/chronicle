package com.github.inxilpro.chronicle.events

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptEventTest {

    @Test
    fun testFileOpenedEventSummary() {
        val event = FileOpenedEvent(path = "/project/src/Main.kt")
        assertEquals("Opened Main.kt", event.summary())
    }

    @Test
    fun testFileOpenedEventSummaryWithInitial() {
        val event = FileOpenedEvent(path = "/project/src/Main.kt", isInitial = true)
        assertEquals("Opened Main.kt (initial)", event.summary())
    }

    @Test
    fun testFileClosedEventSummary() {
        val event = FileClosedEvent(path = "/project/src/Utils.kt")
        assertEquals("Closed Utils.kt", event.summary())
    }

    @Test
    fun testFileSelectedEventSummary() {
        val event = FileSelectedEvent(path = "/project/src/Config.kt")
        assertEquals("Selected Config.kt", event.summary())
    }

    @Test
    fun testRecentFileEventSummary() {
        val event = RecentFileEvent(path = "/project/src/Recent.kt")
        assertEquals("Recent: Recent.kt", event.summary())
    }

    @Test
    fun testSelectionEventSummarySingleLine() {
        val event = SelectionEvent(
            path = "/project/src/Code.kt",
            startLine = 42,
            endLine = 42,
            text = "val x = 1"
        )
        assertEquals("Selected line 42 in Code.kt", event.summary())
    }

    @Test
    fun testSelectionEventSummaryMultipleLines() {
        val event = SelectionEvent(
            path = "/project/src/Code.kt",
            startLine = 10,
            endLine = 20,
            text = "some code"
        )
        assertEquals("Selected lines 10-20 in Code.kt", event.summary())
    }

    @Test
    fun testDocumentChangedEventSummary() {
        val event = DocumentChangedEvent(path = "/project/src/Document.kt", lineCount = 100)
        assertEquals("Modified Document.kt", event.summary())
    }

    @Test
    fun testVisibleAreaEventSummary() {
        val event = VisibleAreaEvent(
            path = "/project/src/View.kt",
            startLine = 1,
            endLine = 50,
            contentDescription = "class View { ... }"
        )
        assertEquals("Viewing View.kt:1-50", event.summary())
    }

    @Test
    fun testFileCreatedEventSummary() {
        val event = FileCreatedEvent(path = "/project/src/NewFile.kt")
        assertEquals("Created NewFile.kt", event.summary())
    }

    @Test
    fun testFileDeletedEventSummary() {
        val event = FileDeletedEvent(path = "/project/src/OldFile.kt")
        assertEquals("Deleted OldFile.kt", event.summary())
    }

    @Test
    fun testFileRenamedEventSummary() {
        val event = FileRenamedEvent(
            oldPath = "/project/src/OldName.kt",
            newPath = "/project/src/NewName.kt"
        )
        assertEquals("Renamed OldName.kt → NewName.kt", event.summary())
    }

    @Test
    fun testFileMovedEventSummary() {
        val event = FileMovedEvent(
            oldPath = "/project/src/File.kt",
            newPath = "/project/lib/File.kt"
        )
        assertEquals("Moved File.kt → File.kt", event.summary())
    }

    @Test
    fun testBranchChangedEventSummary() {
        val event = BranchChangedEvent(
            repository = "/project",
            branch = "feature/new-feature",
            state = "NORMAL"
        )
        assertEquals("Branch: feature/new-feature", event.summary())
    }

    @Test
    fun testBranchChangedEventSummaryDetached() {
        val event = BranchChangedEvent(
            repository = "/project",
            branch = null,
            state = "DETACHED"
        )
        assertEquals("Branch: detached", event.summary())
    }

    @Test
    fun testSearchEventSummary() {
        val event = SearchEvent(query = "findUser")
        assertEquals("Search: findUser", event.summary())
    }

    @Test
    fun testRefactoringEventSummary() {
        val event = RefactoringEvent(
            refactoringType = "Rename",
            details = "Renamed 'foo' to 'bar'"
        )
        assertEquals("Refactoring: Rename", event.summary())
    }

    @Test
    fun testRefactoringUndoEventSummary() {
        val event = RefactoringUndoEvent(refactoringType = "Rename")
        assertEquals("Undo: Rename", event.summary())
    }
}
