package com.github.inxilpro.chronicle.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstantTypeAdapterTest {

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    @Test
    fun testSerializeInstant() {
        val instant = Instant.parse("2026-01-16T12:34:56.789Z")
        val json = gson.toJson(instant)
        assertEquals("\"2026-01-16T12:34:56.789Z\"", json)
    }

    @Test
    fun testSerializeInstantWithoutMillis() {
        val instant = Instant.parse("2026-01-16T12:34:56Z")
        val json = gson.toJson(instant)
        assertEquals("\"2026-01-16T12:34:56Z\"", json)
    }

    @Test
    fun testSerializeCurrentTime() {
        val instant = Instant.now()
        val json = gson.toJson(instant)
        val timestampPattern = Regex(""""[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?Z"""")
        assertTrue(timestampPattern.matches(json))
    }
}
