package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.FileClosedEvent
import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.FileSelectedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FileActivityListenerTest : BasePlatformTestCase() {

    fun testFileOpenedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        // Open a file
        val file = myFixture.configureByText("TestFile.kt", "fun main() {}")

        // The listener should log a FileOpenedEvent
        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val fileOpenedEvents = newEvents.filterIsInstance<FileOpenedEvent>()
        assertTrue("Should have logged a FileOpenedEvent", fileOpenedEvents.isNotEmpty())
        assertTrue("FileOpenedEvent should contain the file path",
            fileOpenedEvents.any { it.path.endsWith("TestFile.kt") })
    }

    fun testFileClosedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        // Open a file first
        val psiFile = myFixture.configureByText("ToClose.kt", "val x = 1")
        val virtualFile = psiFile.virtualFile

        service.resetSession()
        val initialEventCount = service.getEvents().size

        // Close the file
        FileEditorManager.getInstance(project).closeFile(virtualFile)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val fileClosedEvents = newEvents.filterIsInstance<FileClosedEvent>()
        assertTrue("Should have logged a FileClosedEvent", fileClosedEvents.isNotEmpty())
        assertTrue("FileClosedEvent should contain the file path",
            fileClosedEvents.any { it.path.endsWith("ToClose.kt") })
    }

    fun testFileSelectionChangedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        // Open two files
        val file1 = myFixture.configureByText("File1.kt", "val a = 1")
        myFixture.configureByText("File2.kt", "val b = 2")

        service.resetSession()
        val initialEventCount = service.getEvents().size

        // Switch back to file1
        myFixture.openFileInEditor(file1.virtualFile)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val selectionEvents = newEvents.filterIsInstance<FileSelectedEvent>()
        assertTrue("Should have logged a FileSelectedEvent", selectionEvents.isNotEmpty())
    }

    fun testFileOpenedEventHasCorrectType() {
        val event = FileOpenedEvent(path = "/test/file.kt")
        assertEquals("file_opened", event.type)
    }

    fun testFileClosedEventHasCorrectType() {
        val event = FileClosedEvent(path = "/test/file.kt")
        assertEquals("file_closed", event.type)
    }

    fun testFileSelectedEventHasCorrectType() {
        val event = FileSelectedEvent(path = "/test/file.kt", previousPath = "/test/other.kt")
        assertEquals("file_selected", event.type)
    }

    fun testFileSelectedEventTracksPreviousPath() {
        val event = FileSelectedEvent(path = "/test/new.kt", previousPath = "/test/old.kt")
        assertEquals("/test/new.kt", event.path)
        assertEquals("/test/old.kt", event.previousPath)
    }
}
