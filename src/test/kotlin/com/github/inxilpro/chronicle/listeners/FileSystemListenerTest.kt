package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.FileCreatedEvent
import com.github.inxilpro.chronicle.events.FileDeletedEvent
import com.github.inxilpro.chronicle.events.FileMovedEvent
import com.github.inxilpro.chronicle.events.FileRenamedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FileSystemListenerTest : BasePlatformTestCase() {

    fun testFileCreatedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        myFixture.addFileToProject("NewFile.kt", "val x = 1")

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val createEvents = newEvents.filterIsInstance<FileCreatedEvent>()
        assertTrue("Should have logged a FileCreatedEvent",
            createEvents.any { it.path.endsWith("NewFile.kt") })
    }

    fun testFileDeletedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()

        val psiFile = myFixture.addFileToProject("ToDelete.kt", "val x = 1")
        val virtualFile = psiFile.virtualFile

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            virtualFile.delete(this)
        }

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val deleteEvents = newEvents.filterIsInstance<FileDeletedEvent>()
        assertTrue("Should have logged a FileDeletedEvent",
            deleteEvents.any { it.path.endsWith("ToDelete.kt") })
    }

    fun testFileRenamedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()

        val psiFile = myFixture.addFileToProject("OldName.kt", "val x = 1")
        val virtualFile = psiFile.virtualFile

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            virtualFile.rename(this, "NewName.kt")
        }

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val renameEvents = newEvents.filterIsInstance<FileRenamedEvent>()
        assertTrue("Should have logged a FileRenamedEvent", renameEvents.isNotEmpty())

        val renameEvent = renameEvents.first()
        assertTrue("Old path should contain OldName.kt", renameEvent.oldPath.endsWith("OldName.kt"))
        assertTrue("New path should contain NewName.kt", renameEvent.newPath.endsWith("NewName.kt"))
    }

    fun testFileMovedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()

        val psiFile = myFixture.addFileToProject("source/ToMove.kt", "val x = 1")
        val virtualFile = psiFile.virtualFile
        val targetDir = myFixture.addFileToProject("target/placeholder.txt", "").virtualFile.parent

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            virtualFile.move(this, targetDir)
        }

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val moveEvents = newEvents.filterIsInstance<FileMovedEvent>()
        assertTrue("Should have logged a FileMovedEvent", moveEvents.isNotEmpty())

        val moveEvent = moveEvents.first()
        assertTrue("Old path should be in source directory", moveEvent.oldPath.contains("source"))
        assertTrue("New path should be in target directory", moveEvent.newPath.contains("target"))
    }

    fun testFileCreatedEventHasCorrectType() {
        val event = FileCreatedEvent(path = "/test/file.kt")
        assertEquals("file_created", event.type)
    }

    fun testFileDeletedEventHasCorrectType() {
        val event = FileDeletedEvent(path = "/test/file.kt")
        assertEquals("file_deleted", event.type)
    }

    fun testFileRenamedEventHasCorrectType() {
        val event = FileRenamedEvent(oldPath = "/test/old.kt", newPath = "/test/new.kt")
        assertEquals("file_renamed", event.type)
    }

    fun testFileMovedEventHasCorrectType() {
        val event = FileMovedEvent(oldPath = "/src/file.kt", newPath = "/dest/file.kt")
        assertEquals("file_moved", event.type)
    }
}
