package com.github.inxilpro.chronicle.export

import com.github.inxilpro.chronicle.events.TranscriptEvent
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class TranscriptEventTypeAdapter : JsonSerializer<TranscriptEvent> {
    override fun serialize(src: TranscriptEvent, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = context.serialize(src, src::class.java).asJsonObject
        val result = JsonObject()
        result.addProperty("type", src.type)
        jsonObject.entrySet().forEach { (key, value) ->
            result.add(key, value)
        }
        return result
    }
}
