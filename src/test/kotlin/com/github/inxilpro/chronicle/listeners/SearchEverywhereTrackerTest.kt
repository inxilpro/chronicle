package com.github.inxilpro.chronicle.listeners

import com.github.inxilpro.chronicle.events.SearchEvent
import com.github.inxilpro.chronicle.services.ActivityTranscriptService
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SearchEverywhereTrackerTest : BasePlatformTestCase() {

    fun testSearchEventHasCorrectType() {
        val event = SearchEvent(query = "MyClass")
        assertEquals("search", event.type)
    }

    fun testSearchEventSummary() {
        val event = SearchEvent(query = "findMethod")
        assertEquals("Search: findMethod", event.summary())
    }

    fun testSearchEventWithEmptyQuery() {
        val event = SearchEvent(query = "")
        assertEquals("Search: ", event.summary())
    }

    fun testSearchEventTimestampIsSet() {
        val event = SearchEvent(query = "test")
        assertNotNull(event.timestamp)
    }

    fun testSearchEventQueryPreserved() {
        val query = "com.example.MyClass#doSomething"
        val event = SearchEvent(query = query)
        assertEquals(query, event.query)
    }

    fun testTrackerCanBeCreated() {
        val tracker = SearchEverywhereTracker(project)
        assertNotNull(tracker)
        tracker.dispose()
    }

    fun testTrackerDisposeDoesNotThrow() {
        val tracker = SearchEverywhereTracker(project)
        tracker.dispose()
    }

    fun testTrackerTryRegisterDegradeGracefully() {
        val tracker = SearchEverywhereTracker(project)
        // tryRegister should not throw even if the internal API is unavailable
        tracker.tryRegister()
        tracker.dispose()
    }

    fun testTrackerRegisterReturnsTracker() {
        val service = project.service<ActivityTranscriptService>()
        val tracker = SearchEverywhereTracker.register(project, service)
        assertNotNull(tracker)
    }

    fun testSearchEventLoggedCorrectly() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        service.log(SearchEvent(query = "TestQuery"))

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)
        val searchEvents = newEvents.filterIsInstance<SearchEvent>()

        assertTrue("Should have logged a SearchEvent", searchEvents.isNotEmpty())
        assertEquals("TestQuery", searchEvents.last().query)
    }

    fun testMultipleSearchEventsLogged() {
        val service = project.service<ActivityTranscriptService>()
        service.startLogging()
        service.resetSession()
        val initialEventCount = service.getEvents().size

        service.log(SearchEvent(query = "First"))
        service.log(SearchEvent(query = "Second"))
        service.log(SearchEvent(query = "Third"))

        val events = service.getEvents()
        val newEvents = events.drop(initialEventCount)
        val searchEvents = newEvents.filterIsInstance<SearchEvent>()

        assertEquals(3, searchEvents.size)
        assertEquals("First", searchEvents[0].query)
        assertEquals("Second", searchEvents[1].query)
        assertEquals("Third", searchEvents[2].query)
    }
}
