package com.github.inxilpro.chronicle.services

import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant

object EventLogExporter {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun exportToJson(
        events: List<TranscriptEvent>,
        sessionStart: Instant,
        projectName: String
    ): String {
        val root = JsonObject()
        root.addProperty("version", "1.0")
        root.addProperty("projectName", projectName)
        root.addProperty("sessionStart", sessionStart.toString())
        root.addProperty("exportedAt", Instant.now().toString())
        root.addProperty("eventCount", events.size)

        val eventsArray = JsonArray()
        events.forEach { eventsArray.add(it.toJson()) }
        root.add("events", eventsArray)

        return gson.toJson(root)
    }
}
