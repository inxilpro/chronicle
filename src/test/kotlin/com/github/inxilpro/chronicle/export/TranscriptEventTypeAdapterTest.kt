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
        val event = FileOpenedEvent("/test/file.kt", isInitial = true)
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"file_opened\""))
        assertTrue(json.contains("\"/test/file.kt\""))
        assertTrue(json.contains("\"isInitial\":true"))
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
            durationMs = 1500,
            language = "en",
            confidence = 0.95f
        )
        val json = gson.toJson(event, TranscriptEvent::class.java)
        assertTrue(json.contains("\"type\":\"audio_transcription\""))
        assertTrue(json.contains("\"Hello world\""))
        assertTrue(json.contains("\"durationMs\":1500"))
        assertTrue(json.contains("\"confidence\":0.95"))
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
}
