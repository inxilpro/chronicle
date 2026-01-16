package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Instant

object EventLogExporter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant::class.java, InstantSerializer())
        .create()

    fun exportToJson(
        events: List<TranscriptEvent>,
        sessionStart: Instant,
        projectName: String
    ): String {
        val export = EventLogExport(
            version = "1.0",
            projectName = projectName,
            sessionStart = sessionStart,
            exportedAt = Instant.now(),
            eventCount = events.size,
            events = events
        )
        return gson.toJson(export)
    }

    private data class EventLogExport(
        val version: String,
        val projectName: String,
        val sessionStart: Instant,
        val exportedAt: Instant,
        val eventCount: Int,
        val events: List<TranscriptEvent>
    )

    private class InstantSerializer : JsonSerializer<Instant> {
        override fun serialize(src: Instant?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src?.toString())
        }
    }
}
