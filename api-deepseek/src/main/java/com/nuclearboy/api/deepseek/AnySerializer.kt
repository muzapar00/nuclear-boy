package com.nuclearboy.api.deepseek

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for handling JsonElement values in JSON Schema definitions.
 * Uses the built-in JsonElement.serializer() which is a public API.
 */
object AnySerializer : KSerializer<JsonElement> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonElement", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonElement) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return JsonNull
    }
}
