package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.VisibleAreaEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VisibleAreaTrackerTest : BasePlatformTestCase() {

    fun testVisibleAreaEventHasCorrectType() {
        val event = VisibleAreaEvent(
            path = "/test/file.kt",
            startLine = 1,
            endLine = 30
        )
        assertEquals("visible_area", event.type)
    }

    fun testVisibleAreaEventSummary() {
        val event = VisibleAreaEvent(
            path = "/test/SomeFile.kt",
            startLine = 10,
            endLine = 45
        )
        assertEquals("Viewing SomeFile.kt:10-45", event.summary())
    }

    fun testVisibleAreaEventSummaryWithNestedPath() {
        val event = VisibleAreaEvent(
            path = "/home/user/project/src/main/kotlin/MyClass.kt",
            startLine = 1,
            endLine = 100
        )
        assertEquals("Viewing MyClass.kt:1-100", event.summary())
    }

    fun testVisibleAreaEventTimestampIsSet() {
        val event = VisibleAreaEvent(
            path = "/test/file.kt",
            startLine = 1,
            endLine = 10
        )
        assertNotNull(event.timestamp)
    }

    fun testLogVisibleAreaCreatesEvent() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..50).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("TestFile.kt", content)
        myFixture.openFileInEditor(psiFile.virtualFile)

        // In headless test environments, calculateVisibleRange() may return empty range
        val visibleRange = myFixture.editor.calculateVisibleRange()
        if (visibleRange.isEmpty) {
            return
        }

        val tracker = VisibleAreaTracker(project)
        tracker.logVisibleArea(myFixture.editor)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("Should have logged a VisibleAreaEvent", visibleAreaEvents.isNotEmpty())

        val event = visibleAreaEvents.last()
        assertTrue("Path should end with TestFile.kt", event.path.endsWith("TestFile.kt"))
        assertTrue("Start line should be positive", event.startLine >= 1)
        assertTrue("End line should be >= start line", event.endLine >= event.startLine)
    }

    fun testLogVisibleAreaWithDifferentFiles() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content1 = (1..30).joinToString("\n") { "file1_line$it" }
        val psiFile1 = myFixture.configureByText("FirstFile.kt", content1)
        myFixture.openFileInEditor(psiFile1.virtualFile)

        val visibleRange = myFixture.editor.calculateVisibleRange()
        if (visibleRange.isEmpty) {
            return
        }

        val tracker = VisibleAreaTracker(project)
        tracker.logVisibleArea(myFixture.editor)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("Should have logged a VisibleAreaEvent for FirstFile.kt",
            visibleAreaEvents.any { it.path.endsWith("FirstFile.kt") })
    }

    fun testTrackerAttachesToEditor() {
        val content = (1..20).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("AttachTest.kt", content)
        myFixture.openFileInEditor(psiFile.virtualFile)

        val tracker = VisibleAreaTracker(project)
        tracker.attachTo(myFixture.editor)

        // Attaching again should be a no-op (returns early)
        tracker.attachTo(myFixture.editor)

        // Clean up
        tracker.dispose()
    }

    fun testTrackerDisposeCleansUp() {
        val tracker = VisibleAreaTracker(project)
        tracker.dispose()
    }

    fun testVisibleAreaEventLineRangeIsCorrect() {
        val event = VisibleAreaEvent(
            path = "/test/file.kt",
            startLine = 15,
            endLine = 45
        )
        assertEquals(15, event.startLine)
        assertEquals(45, event.endLine)
    }
}
