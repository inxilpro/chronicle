package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.*
import com.google.gson.GsonBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TranscriptEventTypeAdapterTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .registerTypeAdapter(TranscriptEvent::class.java, TranscriptEventTypeAdapter())
        .create()

    @Test
    fun testSerializeFileOpenedEvent() {
        val event = FileOpenedEvent("/test/file.kt")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"file_opened\""))
        assertTrue(json.contains("\"/test/file.kt\""))
    }

    @Test
    fun testSerializeSelectionEvent() {
        val event = SelectionEvent("/test.kt", 10, 20, "selected text")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"selection\""))
        assertTrue(json.contains("\"startLine\":10"))
        assertTrue(json.contains("\"endLine\":20"))
        assertTrue(json.contains("\"selected text\""))
    }

    @Test
    fun testSerializeAudioTranscriptionEvent() {
        val event = AudioTranscriptionEvent(
            transcriptionText = "Hello world",
            confidence = 0.3f
        )
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"audio_transcription\""))
        assertTrue(json.contains("\"Hello world\""))
        assertTrue(json.contains("\"confidence\":0.3"))
        assertFalse("Should not contain durationMs", json.contains("durationMs"))
        assertFalse("Should not contain language", json.contains("language"))
    }

    @Test
    fun testSerializeAudioTranscriptionEventOmitsNullConfidence() {
        val event = AudioTranscriptionEvent(transcriptionText = "Hello world")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"audio_transcription\""))
        assertFalse("Null confidence should be omitted", json.contains("confidence"))
    }

    @Test
    fun testSerializeBranchChangedEvent() {
        val event = BranchChangedEvent("/repo", "main", "NORMAL")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"branch_changed\""))
        assertTrue(json.contains("\"branch\":\"main\""))
        assertTrue(json.contains("\"state\":\"NORMAL\""))
    }

    @Test
    fun testSerializeFileRenamedEvent() {
        val event = FileRenamedEvent("/old/path.kt", "/new/path.kt")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"file_renamed\""))
        assertTrue(json.contains("\"oldPath\":\"/old/path.kt\""))
        assertTrue(json.contains("\"newPath\":\"/new/path.kt\""))
    }

    @Test
    fun testDoesNotIncludeSummary() {
        val event = FileOpenedEvent("/test.kt")
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertFalse(json.contains("\"summary\""))
        assertFalse(json.contains("Opened"))
    }

    @Test
    fun testVisibleAreaEventOmitsContentDescription() {
        val event = VisibleAreaEvent("/test.kt", 1, 30)
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"visible_area\""))
        assertTrue(json.contains("\"startLine\":1"))
        assertTrue(json.contains("\"endLine\":30"))
        assertFalse("Should not contain contentDescription", json.contains("contentDescription"))
    }
}
