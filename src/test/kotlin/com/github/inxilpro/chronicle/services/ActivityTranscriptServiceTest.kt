package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.FileOpenedEvent
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

        val testEvent = FileOpenedEvent(path = "/test/path/file.kt")
        service.log(testEvent)

        val events = service.getEvents()
        assertEquals(initialCount + 1, events.size)

        val lastEvent = events.last()
        assertTrue(lastEvent is FileOpenedEvent)
        assertEquals("/test/path/file.kt", (lastEvent as FileOpenedEvent).path)
    }

    fun testResetSessionClearsEvents() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        // Log some test events
        service.log(FileOpenedEvent(path = "/test/file1.kt"))
        service.log(FileOpenedEvent(path = "/test/file2.kt"))

        val originalStart = service.getSessionStart()

        // Small delay to ensure new session has different timestamp
        Thread.sleep(10)

        service.resetSession()

        // Session start should be updated
        assertTrue(service.getSessionStart() >= originalStart)

        // After reset, events should be empty (no initial state capture)
        assertTrue(service.getEvents().isEmpty())
    }

    fun testStartLoggingUpdatesSessionStartOnFreshSession() {
        val service = project.service<ActivityTranscriptService>()

        // Reset the initialization flag to simulate a fresh session
        service.hasInitializedSession = false

        val initialStart = service.getSessionStart()

        Thread.sleep(50)

        val beforeStartLogging = java.time.Instant.now()

        service.startLogging()

        val newSessionStart = service.getSessionStart()

        assertTrue(
            "Session start should be updated when logging starts on a fresh session",
            newSessionStart > initialStart
        )

        assertTrue(
            "Session start should be approximately when startLogging was called",
            newSessionStart >= beforeStartLogging
        )
    }

    fun testNoInitialStateCaptured() {
        myFixture.configureByText("TestFile.kt", "fun main() {}")

        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()

        val events = service.getEvents()

        // Should NOT have any initial file opened events or recent file events
        assertTrue("Reset session should produce empty events list", events.isEmpty())
    }

    fun testEventTypes() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()

        val fileOpened = FileOpenedEvent(path = "/test.kt")
        service.log(fileOpened)

        assertEquals("file_opened", fileOpened.type)
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

    fun testGetProjectBasePath() {
        val service = project.service<ActivityTranscriptService>()
        // basePath should exist for test projects
        assertNotNull(service.getProjectBasePath())
    }
}
