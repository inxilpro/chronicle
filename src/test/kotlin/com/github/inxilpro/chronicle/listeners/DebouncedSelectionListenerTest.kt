package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.SelectionEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DebouncedSelectionListenerTest : BasePlatformTestCase() {

    fun testSelectionEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("SelectionTest.kt", """
            fun main() {
                val hello = "world"
                println(hello)
            }
        """.trimIndent())

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.selectionModel.setSelection(0, 8)

        // Wait for debounce (300ms default + buffer)
        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()
        assertTrue("Should have logged a SelectionEvent", selectionEvents.isNotEmpty())
    }

    fun testSelectionEventContainsCorrectPath() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("PathTest.kt", "val x = 1")

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.selectionModel.setSelection(0, 5)

        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()
        assertTrue("SelectionEvent should contain the file path",
            selectionEvents.any { it.path.endsWith("PathTest.kt") })
    }

    fun testSelectionEventTracksLineNumbers() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("LineTest.kt", """
            line1
            line2
            line3
            line4
        """.trimIndent())

        myFixture.openFileInEditor(psiFile.virtualFile)

        // Select from start of line2 to end of line3
        val doc = myFixture.editor.document
        val startOffset = doc.getLineStartOffset(1)
        val endOffset = doc.getLineEndOffset(2)
        myFixture.editor.selectionModel.setSelection(startOffset, endOffset)

        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()
        assertTrue("Should have logged a SelectionEvent", selectionEvents.isNotEmpty())

        val selectionEvent = selectionEvents.last()
        assertEquals("Start line should be 2 (1-indexed)", 2, selectionEvent.startLine)
        assertEquals("End line should be 3 (1-indexed)", 3, selectionEvent.endLine)
    }

    fun testSelectionEventCapturesSelectedText() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("TextTest.kt", "val selected = 42")

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.selectionModel.setSelection(4, 12)

        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()
        assertTrue("Should have logged a SelectionEvent", selectionEvents.isNotEmpty())

        val selectionEvent = selectionEvents.last()
        assertEquals("selected", selectionEvent.text)
    }

    fun testNoEventLoggedWithoutSelection() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()

        val psiFile = myFixture.configureByText("NoSelection.kt", "val x = 1")
        myFixture.openFileInEditor(psiFile.virtualFile)

        // Wait without making a selection
        Thread.sleep(400)

        val initialEventCount = service.getEvents().size

        // Remove any selection
        myFixture.editor.selectionModel.removeSelection()

        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()
        assertTrue("Should not log SelectionEvent without active selection", selectionEvents.isEmpty())
    }

    fun testDebounceOnlyLogsLastSelection() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("DebounceTest.kt", """
            first
            second
            third
        """.trimIndent())

        myFixture.openFileInEditor(psiFile.virtualFile)

        // Make rapid selections (each within debounce window)
        myFixture.editor.selectionModel.setSelection(0, 5) // "first"
        Thread.sleep(50)
        myFixture.editor.selectionModel.setSelection(6, 12) // "second"
        Thread.sleep(50)
        myFixture.editor.selectionModel.setSelection(13, 18) // "third"

        // Wait for debounce to complete
        Thread.sleep(400)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<SelectionEvent>()

        // Should only have one event (the last selection)
        assertEquals("Debounce should result in only one SelectionEvent", 1, selectionEvents.size)
        assertEquals("Should capture the last selection text", "third", selectionEvents.first().text)
    }

    fun testSelectionEventHasCorrectType() {
        val event = SelectionEvent(
            path = "/test/file.kt",
            startLine = 1,
            endLine = 5,
            text = "selected text"
        )
        assertEquals("selection", event.type)
    }

    fun testSelectionEventTextCanBeNull() {
        val event = SelectionEvent(
            path = "/test/file.kt",
            startLine = 1,
            endLine = 1,
            text = null
        )
        assertNull(event.text)
    }
}
