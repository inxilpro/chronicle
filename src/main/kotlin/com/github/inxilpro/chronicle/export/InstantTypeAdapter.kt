package com.github.inxilpro.chronicle.export

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.Instant

class InstantTypeAdapter : JsonSerializer<Instant> {
    override fun serialize(src: Instant, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(src.toString())
    }
}
