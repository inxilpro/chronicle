package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.FileCreatedEvent
import com.github.inxilpro.chronicle.events.FileDeletedEvent
import com.github.inxilpro.chronicle.events.FileMovedEvent
import com.github.inxilpro.chronicle.events.FileRenamedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FileSystemListenerTest : BasePlatformTestCase() {

    fun testFileCreatedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        runWriteAction {
            sourceRoot.createChildData(this, "NewFile.kt")
        }

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val createEvents = newEvents.filterIsInstance<FileCreatedEvent>()
        assertTrue("Should have logged a FileCreatedEvent",
            createEvents.any { it.path.endsWith("NewFile.kt") })
    }

    fun testFileDeletedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()

        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        val file = runWriteAction {
            sourceRoot.createChildData(this, "ToDelete.kt")
        }

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            file.delete(this)
        }

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val deleteEvents = newEvents.filterIsInstance<FileDeletedEvent>()
        assertTrue("Should have logged a FileDeletedEvent",
            deleteEvents.any { it.path.endsWith("ToDelete.kt") })
    }

    fun testFileRenamedEventIsLogged() {
        val service = project.service<ActivityTranscriptService>()

        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        val file = runWriteAction {
            sourceRoot.createChildData(this, "OldName.kt")
        }

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            file.rename(this, "NewName.kt")
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

        val sourceDir = myFixture.tempDirFixture.findOrCreateDir("source")
        val targetDir = myFixture.tempDirFixture.findOrCreateDir("target")
        val file = runWriteAction {
            sourceDir.createChildData(this, "ToMove.kt")
        }

        service.resetSession()
        val initialEventCount = service.getEvents().size

        runWriteAction {
            file.move(this, targetDir)
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
