package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.DocumentChangedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocumentChangeListenerTest : BasePlatformTestCase() {

    fun testDocumentChangeEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("DocChange.kt", "val x = 1")
        myFixture.openFileInEditor(psiFile.virtualFile)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("val x = 2")
        }
        service.documentChangeListener?.flushPendingEvents()

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val docEvents = newEvents.filterIsInstance<DocumentChangedEvent>()
        assertTrue("Should have logged a DocumentChangedEvent", docEvents.isNotEmpty())
    }

    fun testDocumentChangeEventContainsCorrectPath() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("PathChange.kt", "val x = 1")
        myFixture.openFileInEditor(psiFile.virtualFile)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("val x = 2")
        }
        service.documentChangeListener?.flushPendingEvents()

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val docEvents = newEvents.filterIsInstance<DocumentChangedEvent>()
        assertTrue("DocumentChangedEvent should contain the file path",
            docEvents.any { it.path.endsWith("PathChange.kt") })
    }

    fun testDocumentChangeEventTracksLineCount() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("LineCount.kt", "line1")
        myFixture.openFileInEditor(psiFile.virtualFile)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("line1\nline2\nline3")
        }
        service.documentChangeListener?.flushPendingEvents()

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val docEvents = newEvents.filterIsInstance<DocumentChangedEvent>()
        assertTrue("Should have logged a DocumentChangedEvent", docEvents.isNotEmpty())

        val lastEvent = docEvents.last()
        assertEquals("Should have 3 lines", 3, lastEvent.lineCount)
    }

    fun testDocumentChangeEventHasCorrectType() {
        val event = DocumentChangedEvent(
            path = "/test/file.kt",
            lineCount = 10
        )
        assertEquals("document_changed", event.type)
    }

    fun testMultipleChangesLogMultipleEvents() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val psiFile = myFixture.configureByText("MultiChange.kt", "line1")
        myFixture.openFileInEditor(psiFile.virtualFile)

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("line1\nline2")
        }
        service.documentChangeListener?.flushPendingEvents()

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText("line1\nline2\nline3")
        }
        service.documentChangeListener?.flushPendingEvents()

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val docEvents = newEvents.filterIsInstance<DocumentChangedEvent>()
        assertTrue("Should have logged multiple DocumentChangedEvents", docEvents.size >= 2)
    }
}
