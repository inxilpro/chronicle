package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.VisibleAreaEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class VisibleAreaTrackerTest : BasePlatformTestCase() {

    fun testVisibleAreaEventIsLoggedOnScroll() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..100).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("ScrollTest.kt", content)

        myFixture.openFileInEditor(psiFile.virtualFile)

        // Scroll to a different position
        myFixture.editor.scrollingModel.scrollVertically(200)

        // Wait for debounce (500ms default + buffer)
        Thread.sleep(700)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("Should have logged a VisibleAreaEvent", visibleAreaEvents.isNotEmpty())
    }

    fun testVisibleAreaEventContainsCorrectPath() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..50).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("PathTest.kt", content)

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.scrollingModel.scrollVertically(100)

        Thread.sleep(700)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("VisibleAreaEvent should contain the file path",
            visibleAreaEvents.any { it.path.endsWith("PathTest.kt") })
    }

    fun testVisibleAreaEventTracksLineNumbers() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..100).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("LineTest.kt", content)

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.scrollingModel.scrollVertically(0)

        Thread.sleep(700)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("Should have logged a VisibleAreaEvent", visibleAreaEvents.isNotEmpty())

        val visibleAreaEvent = visibleAreaEvents.last()
        assertTrue("Start line should be positive", visibleAreaEvent.startLine >= 1)
        assertTrue("End line should be >= start line", visibleAreaEvent.endLine >= visibleAreaEvent.startLine)
    }

    fun testDebounceOnlyLogsLastVisibleArea() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..100).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("DebounceTest.kt", content)

        myFixture.openFileInEditor(psiFile.virtualFile)

        // Make rapid scroll changes (each within debounce window)
        myFixture.editor.scrollingModel.scrollVertically(100)
        Thread.sleep(100)
        myFixture.editor.scrollingModel.scrollVertically(200)
        Thread.sleep(100)
        myFixture.editor.scrollingModel.scrollVertically(300)

        // Wait for debounce to complete
        Thread.sleep(700)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()

        // Should only have one event (the last scroll position)
        assertEquals("Debounce should result in only one VisibleAreaEvent", 1, visibleAreaEvents.size)
    }

    fun testVisibleAreaEventHasCorrectType() {
        val event = VisibleAreaEvent(
            path = "/test/file.kt",
            startLine = 1,
            endLine = 30,
            contentDescription = "Lines 1-30"
        )
        assertEquals("visible_area", event.type)
    }

    fun testVisibleAreaEventSummary() {
        val event = VisibleAreaEvent(
            path = "/test/SomeFile.kt",
            startLine = 10,
            endLine = 45,
            contentDescription = "Lines 10-45"
        )
        assertEquals("Viewing SomeFile.kt:10-45", event.summary())
    }

    fun testContentDescriptionContainsLineRange() {
        val service = project.service<ActivityTranscriptService>()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        val content = (1..50).joinToString("\n") { "line$it" }
        val psiFile = myFixture.configureByText("DescTest.kt", content)

        myFixture.openFileInEditor(psiFile.virtualFile)
        myFixture.editor.scrollingModel.scrollVertically(0)

        Thread.sleep(700)

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)

        val visibleAreaEvents = newEvents.filterIsInstance<VisibleAreaEvent>()
        assertTrue("Should have logged a VisibleAreaEvent", visibleAreaEvents.isNotEmpty())

        val event = visibleAreaEvents.last()
        assertTrue("Content description should contain line range",
            event.contentDescription.contains("Lines"))
    }
}
