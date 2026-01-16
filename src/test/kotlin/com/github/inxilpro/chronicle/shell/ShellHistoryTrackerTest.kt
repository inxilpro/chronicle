package com.github.inxilpro.chronicle.shell

import com.github.inxilpro.chronicle.events.ShellCommandEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.time.Instant

class ShellHistoryTrackerTest : BasePlatformTestCase() {

    fun testShellHistoryTrackerRegistered() {
        val service = project.service<ActivityTranscriptService>()
        assertNotNull("ShellHistoryTracker should be registered", service.shellHistoryTracker)
    }

    fun testStartTrackingInitializesBackfill() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()

        val tracker = service.shellHistoryTracker
        assertNotNull(tracker)

        tracker!!.backfillHistory()
    }

    fun testBackfillLogsShellCommandEvents() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()

        val mockParser = object : ShellHistoryParser() {
            override fun parseRecentCommands(since: Instant, limit: Int): List<ShellCommand> {
                return listOf(
                    ShellCommand("echo test", Instant.now(), "bash"),
                    ShellCommand("git status", Instant.now(), "zsh")
                )
            }
        }

        val tracker = ShellHistoryTracker(project, parser = mockParser)
        tracker.startTracking(Instant.now().minusSeconds(60))
        tracker.backfillHistory()

        val shellEvents = service.getEvents().filterIsInstance<ShellCommandEvent>()
        assertTrue("Should have logged shell command events", shellEvents.size >= 2)
    }

    fun testShellCommandEventHasCorrectType() {
        val event = ShellCommandEvent(
            command = "echo hello",
            shell = "bash",
            timestamp = Instant.now()
        )
        assertEquals("shell_command", event.type)
    }

    fun testShellCommandEventSummary() {
        val event = ShellCommandEvent(
            command = "git commit -m 'test message'",
            shell = "zsh",
            timestamp = Instant.now()
        )
        assertTrue(event.summary().contains("[zsh]"))
        assertTrue(event.summary().contains("git commit"))
    }

    fun testShellCommandEventSummaryTruncatesLongCommands() {
        val longCommand = "a".repeat(100)
        val event = ShellCommandEvent(
            command = longCommand,
            shell = "bash",
            timestamp = Instant.now()
        )
        assertTrue(event.summary().length < longCommand.length + 20)
        assertTrue(event.summary().endsWith("..."))
    }

    fun testDeduplicatesCommands() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialCount = service.getEvents().filterIsInstance<ShellCommandEvent>().size

        val timestamp = Instant.now()
        val mockParser = object : ShellHistoryParser() {
            override fun parseRecentCommands(since: Instant, limit: Int): List<ShellCommand> {
                return listOf(
                    ShellCommand("echo test", timestamp, "bash")
                )
            }
        }

        val tracker = ShellHistoryTracker(project, parser = mockParser)
        tracker.startTracking(timestamp.minusSeconds(60))
        tracker.backfillHistory()
        tracker.backfillHistory()

        val shellEvents = service.getEvents().filterIsInstance<ShellCommandEvent>()
        val newEvents = shellEvents.size - initialCount
        assertEquals("Should deduplicate commands", 1, newEvents)
    }

    fun testStopTrackingTriggersBackfill() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()

        var backfillCalled = false
        val mockParser = object : ShellHistoryParser() {
            override fun parseRecentCommands(since: Instant, limit: Int): List<ShellCommand> {
                backfillCalled = true
                return emptyList()
            }
        }

        val tracker = ShellHistoryTracker(project, parser = mockParser)
        tracker.startTracking(Instant.now().minusSeconds(60))
        backfillCalled = false
        tracker.stopTracking()

        assertTrue("stopTracking should trigger a final backfill", backfillCalled)
    }
}
