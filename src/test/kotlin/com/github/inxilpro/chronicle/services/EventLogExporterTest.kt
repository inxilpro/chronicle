package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.*
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventLogExporterTest {

    private val testSessionStart = Instant.parse("2024-01-15T10:00:00Z")
    private val testTimestamp = Instant.parse("2024-01-15T10:05:00Z")

    @Test
    fun testExportEmptyEventList() {
        val json = EventLogExporter.exportToJson(
            events = emptyList(),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("1.0", root.get("version").asString)
        assertEquals("TestProject", root.get("projectName").asString)
        assertEquals(testSessionStart.toString(), root.get("sessionStart").asString)
        assertEquals(0, root.get("eventCount").asInt)
        assertTrue(root.get("events").asJsonArray.isEmpty)
    }

    @Test
    fun testExportFileOpenedEvent() {
        val event = FileOpenedEvent(
            path = "/project/src/Main.kt",
            isInitial = true,
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val root = JsonParser.parseString(json).asJsonObject
        val events = root.get("events").asJsonArray
        assertEquals(1, events.size())

        val exported = events[0].asJsonObject
        assertEquals("file_opened", exported.get("type").asString)
        assertEquals(testTimestamp.toString(), exported.get("timestamp").asString)
        assertEquals("/project/src/Main.kt", exported.get("path").asString)
        assertTrue(exported.get("isInitial").asBoolean)
    }

    @Test
    fun testExportFileClosedEvent() {
        val event = FileClosedEvent(
            path = "/project/src/Utils.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_closed", exported.get("type").asString)
        assertEquals("/project/src/Utils.kt", exported.get("path").asString)
    }

    @Test
    fun testExportFileSelectedEvent() {
        val event = FileSelectedEvent(
            path = "/project/src/Config.kt",
            previousPath = "/project/src/Main.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_selected", exported.get("type").asString)
        assertEquals("/project/src/Config.kt", exported.get("path").asString)
        assertEquals("/project/src/Main.kt", exported.get("previousPath").asString)
    }

    @Test
    fun testExportSelectionEvent() {
        val event = SelectionEvent(
            path = "/project/src/Code.kt",
            startLine = 10,
            endLine = 20,
            text = "selected text",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("selection", exported.get("type").asString)
        assertEquals("/project/src/Code.kt", exported.get("path").asString)
        assertEquals(10, exported.get("startLine").asInt)
        assertEquals(20, exported.get("endLine").asInt)
        assertEquals("selected text", exported.get("text").asString)
    }

    @Test
    fun testExportDocumentChangedEvent() {
        val event = DocumentChangedEvent(
            path = "/project/src/Document.kt",
            lineCount = 150,
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("document_changed", exported.get("type").asString)
        assertEquals(150, exported.get("lineCount").asInt)
    }

    @Test
    fun testExportVisibleAreaEvent() {
        val event = VisibleAreaEvent(
            path = "/project/src/View.kt",
            startLine = 1,
            endLine = 50,
            contentDescription = "class View { ... }",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("visible_area", exported.get("type").asString)
        assertEquals(1, exported.get("startLine").asInt)
        assertEquals(50, exported.get("endLine").asInt)
        assertEquals("class View { ... }", exported.get("contentDescription").asString)
    }

    @Test
    fun testExportFileRenamedEvent() {
        val event = FileRenamedEvent(
            oldPath = "/project/src/OldName.kt",
            newPath = "/project/src/NewName.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_renamed", exported.get("type").asString)
        assertEquals("/project/src/OldName.kt", exported.get("oldPath").asString)
        assertEquals("/project/src/NewName.kt", exported.get("newPath").asString)
    }

    @Test
    fun testExportBranchChangedEvent() {
        val event = BranchChangedEvent(
            repository = "/project",
            branch = "feature/test",
            state = "NORMAL",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("branch_changed", exported.get("type").asString)
        assertEquals("/project", exported.get("repository").asString)
        assertEquals("feature/test", exported.get("branch").asString)
        assertEquals("NORMAL", exported.get("state").asString)
    }

    @Test
    fun testExportSearchEvent() {
        val event = SearchEvent(
            query = "findUser",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("search", exported.get("type").asString)
        assertEquals("findUser", exported.get("query").asString)
    }

    @Test
    fun testExportRefactoringEvent() {
        val event = RefactoringEvent(
            refactoringType = "Rename",
            details = "Renamed 'foo' to 'bar'",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("refactoring", exported.get("type").asString)
        assertEquals("Rename", exported.get("refactoringType").asString)
        assertEquals("Renamed 'foo' to 'bar'", exported.get("details").asString)
    }

    @Test
    fun testExportAudioTranscriptionEvent() {
        val event = AudioTranscriptionEvent(
            transcriptionText = "Hello, this is a test.",
            durationMs = 5000,
            language = "en",
            confidence = 0.95f,
            speakerSegment = 1,
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("audio_transcription", exported.get("type").asString)
        assertEquals("Hello, this is a test.", exported.get("transcriptionText").asString)
        assertEquals(5000, exported.get("durationMs").asLong)
        assertEquals("en", exported.get("language").asString)
        assertEquals(0.95f, exported.get("confidence").asFloat, 0.001f)
        assertEquals(1, exported.get("speakerSegment").asInt)
    }

    @Test
    fun testExportMultipleEvents() {
        val events = listOf(
            FileOpenedEvent(path = "/project/src/A.kt", timestamp = testTimestamp),
            FileSelectedEvent(path = "/project/src/B.kt", timestamp = testTimestamp.plusSeconds(10)),
            DocumentChangedEvent(path = "/project/src/A.kt", lineCount = 100, timestamp = testTimestamp.plusSeconds(20))
        )

        val json = EventLogExporter.exportToJson(
            events = events,
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val root = JsonParser.parseString(json).asJsonObject
        assertEquals(3, root.get("eventCount").asInt)
        assertEquals(3, root.get("events").asJsonArray.size())
    }

    @Test
    fun testExportNullValues() {
        val event = FileSelectedEvent(
            path = "/project/src/Config.kt",
            previousPath = null,
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertTrue(exported.has("previousPath"))
        assertTrue(exported.get("previousPath").isJsonNull)
    }

    @Test
    fun testExportedAtIsPresent() {
        val json = EventLogExporter.exportToJson(
            events = emptyList(),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val root = JsonParser.parseString(json).asJsonObject
        assertTrue(root.has("exportedAt"))
        assertNotNull(root.get("exportedAt").asString)
    }

    @Test
    fun testExportRecentFileEvent() {
        val event = RecentFileEvent(
            path = "/project/src/Recent.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("recent_file", exported.get("type").asString)
        assertEquals("/project/src/Recent.kt", exported.get("path").asString)
    }

    @Test
    fun testExportFileCreatedEvent() {
        val event = FileCreatedEvent(
            path = "/project/src/NewFile.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_created", exported.get("type").asString)
        assertEquals("/project/src/NewFile.kt", exported.get("path").asString)
    }

    @Test
    fun testExportFileDeletedEvent() {
        val event = FileDeletedEvent(
            path = "/project/src/OldFile.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_deleted", exported.get("type").asString)
        assertEquals("/project/src/OldFile.kt", exported.get("path").asString)
    }

    @Test
    fun testExportFileMovedEvent() {
        val event = FileMovedEvent(
            oldPath = "/project/src/File.kt",
            newPath = "/project/lib/File.kt",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("file_moved", exported.get("type").asString)
        assertEquals("/project/src/File.kt", exported.get("oldPath").asString)
        assertEquals("/project/lib/File.kt", exported.get("newPath").asString)
    }

    @Test
    fun testExportRefactoringUndoEvent() {
        val event = RefactoringUndoEvent(
            refactoringType = "Rename",
            timestamp = testTimestamp
        )

        val json = EventLogExporter.exportToJson(
            events = listOf(event),
            sessionStart = testSessionStart,
            projectName = "TestProject"
        )

        val events = JsonParser.parseString(json).asJsonObject.get("events").asJsonArray
        val exported = events[0].asJsonObject
        assertEquals("refactoring_undo", exported.get("type").asString)
        assertEquals("Rename", exported.get("refactoringType").asString)
    }
}
