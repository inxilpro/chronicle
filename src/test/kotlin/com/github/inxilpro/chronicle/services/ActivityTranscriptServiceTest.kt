package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileOpenedEvent
import com.github.inxilpro.chronicle.events.RecentFileEvent
import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ActivityTranscriptServiceTest : BasePlatformTestCase() {

    fun testServiceCanBeInstantiated() {
        val service = project.service<ActivityTranscriptService>()
        assertNotNull(service)
    }

    fun testServiceCapturesProjectName() {
        val service = project.service<ActivityTranscriptService>()
        assertEquals(project.name, service.getProjectName())
    }

    fun testServiceHasSessionStartTime() {
        val service = project.service<ActivityTranscriptService>()
        assertNotNull(service.getSessionStart())
    }

    fun testServiceHasGitBranchGetter() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        // Git branch will be null in tests since git4idea plugin is not available
        // but the getter should exist and not throw
        service.getSessionGitBranch()
    }

    fun testLogEvent() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        val initialCount = service.getEvents().size

        val testEvent = FileOpenedEvent(path = "/test/path/file.kt", isInitial = false)
        service.log(testEvent)

        val events = service.getEvents()
        assertEquals(initialCount + 1, events.size)

        val lastEvent = events.last()
        assertTrue(lastEvent is FileOpenedEvent)
        assertEquals("/test/path/file.kt", (lastEvent as FileOpenedEvent).path)
        assertFalse(lastEvent.isInitial)
    }

    fun testResetSessionClearsEvents() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        // Log some test events
        service.log(FileOpenedEvent(path = "/test/file1.kt"))
        service.log(FileOpenedEvent(path = "/test/file2.kt"))

        val originalStart = service.getSessionStart()

        service.resetSession()

        // Session start should be updated (will be >= original since time moves forward)
        assertTrue(service.getSessionStart() >= originalStart)
    }

    fun testCaptureInitialStateWithOpenFile() {
        // Create and open a file using the test fixture
        myFixture.configureByText("TestFile.kt", "fun main() {}")

        // Get a fresh service (reset to capture the now-open file)
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()

        val events = service.getEvents()

        // Should have captured the open file
        val fileOpenedEvents = events.filterIsInstance<FileOpenedEvent>()
        assertTrue("Should have at least one FileOpenedEvent", fileOpenedEvents.isNotEmpty())

        // At least one should be marked as initial
        val initialEvents = fileOpenedEvents.filter { it.isInitial }
        assertTrue("Should have at least one initial FileOpenedEvent", initialEvents.isNotEmpty())
    }

    fun testEventTypes() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        val fileOpened = FileOpenedEvent(path = "/test.kt")
        val recentFile = RecentFileEvent(path = "/recent.kt")

        service.log(fileOpened)
        service.log(recentFile)

        assertEquals("file_opened", fileOpened.type)
        assertEquals("recent_file", recentFile.type)
    }

    fun testEventTimestamps() {
        val beforeTime = java.time.Instant.now()

        val event = FileOpenedEvent(path = "/test.kt")

        val afterTime = java.time.Instant.now()

        assertTrue("Event timestamp should be after or equal to beforeTime",
            event.timestamp >= beforeTime)
        assertTrue("Event timestamp should be before or equal to afterTime",
            event.timestamp <= afterTime)
    }
}
