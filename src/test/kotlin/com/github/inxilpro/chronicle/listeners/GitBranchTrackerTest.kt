package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.BranchChangedEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GitBranchTrackerTest : BasePlatformTestCase() {

    fun testBranchChangedEventHasCorrectType() {
        val event = BranchChangedEvent(
            repository = "/path/to/repo",
            branch = "main",
            state = "NORMAL"
        )
        assertEquals("branch_changed", event.type)
    }

    fun testBranchChangedEventSummary() {
        val event = BranchChangedEvent(
            repository = "/path/to/repo",
            branch = "feature/new-feature",
            state = "NORMAL"
        )
        assertEquals("Branch: feature/new-feature", event.summary())
    }

    fun testBranchChangedEventSummaryWithDetachedHead() {
        val event = BranchChangedEvent(
            repository = "/path/to/repo",
            branch = null,
            state = "DETACHED"
        )
        assertEquals("Branch: detached", event.summary())
    }

    fun testBranchChangedEventTimestampIsSet() {
        val event = BranchChangedEvent(
            repository = "/path/to/repo",
            branch = "main",
            state = "NORMAL"
        )
        assertNotNull(event.timestamp)
    }

    fun testBranchChangedEventFieldsPreserved() {
        val event = BranchChangedEvent(
            repository = "/home/user/my-project",
            branch = "develop",
            state = "REBASING"
        )
        assertEquals("/home/user/my-project", event.repository)
        assertEquals("develop", event.branch)
        assertEquals("REBASING", event.state)
    }

    fun testTrackerCanBeCreated() {
        val tracker = GitBranchTracker(project)
        assertNotNull(tracker)
        tracker.dispose()
    }

    fun testTrackerDisposeDoesNotThrow() {
        val tracker = GitBranchTracker(project)
        tracker.dispose()
    }

    fun testTrackerTryRegisterDegradesGracefully() {
        val tracker = GitBranchTracker(project)
        // tryRegister should not throw even if git4idea is unavailable
        tracker.tryRegister()
        tracker.dispose()
    }

    fun testTrackerRegisterReturnsTracker() {
        val service = project.service<ActivityTranscriptService>()
        val tracker = GitBranchTracker.register(project, service)
        assertNotNull(tracker)
    }

    fun testBranchChangedEventLoggedCorrectly() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        service.log(BranchChangedEvent(
            repository = "/path/to/repo",
            branch = "main",
            state = "NORMAL"
        ))

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)
        val branchEvents = newEvents.filterIsInstance<BranchChangedEvent>()

        assertTrue("Should have logged a BranchChangedEvent", branchEvents.isNotEmpty())
        assertEquals("main", branchEvents.last().branch)
    }

    fun testMultipleBranchChangedEventsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        service.log(BranchChangedEvent(
            repository = "/repo",
            branch = "main",
            state = "NORMAL"
        ))
        service.log(BranchChangedEvent(
            repository = "/repo",
            branch = "feature/a",
            state = "NORMAL"
        ))
        service.log(BranchChangedEvent(
            repository = "/repo",
            branch = "feature/b",
            state = "MERGING"
        ))

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)
        val branchEvents = newEvents.filterIsInstance<BranchChangedEvent>()

        assertEquals(3, branchEvents.size)
        assertEquals("main", branchEvents[0].branch)
        assertEquals("feature/a", branchEvents[1].branch)
        assertEquals("feature/b", branchEvents[2].branch)
        assertEquals("MERGING", branchEvents[2].state)
    }

    fun testBranchChangedWithShortRevision() {
        val event = BranchChangedEvent(
            repository = "/path/to/repo",
            branch = "abc1234f",
            state = "DETACHED"
        )
        assertEquals("Branch: abc1234f", event.summary())
    }
}
