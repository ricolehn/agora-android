package org.agora.app.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Parses German or English decimal strings the way the backend does ("1.234,56" -> 1234.56). */
fun parseAmount(raw: String?): Double? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim().replace(" ", "").replace("€", "")
    val normalized = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
    return normalized.toDoubleOrNull()
}

/** Ids are strings for new records but plain numbers (timestamps) for legacy ones. */
object FlexStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val element = decoder.decodeJsonElement()
        if (element is JsonNull || element !is JsonPrimitive) return ""
        return element.contentOrNull ?: ""
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Amounts are numbers, but legacy data may hold German decimal strings. */
object FlexDoubleSerializer : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        if (decoder !is JsonDecoder) return decoder.decodeDouble()
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive || element is JsonNull) return 0.0
        return if (element.isString) parseAmount(element.content) ?: 0.0 else element.content.toDoubleOrNull() ?: 0.0
    }
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

/** Integers that may arrive as strings or be missing/null. */
object FlexIntSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        if (element !is JsonPrimitive || element is JsonNull) return 0
        return element.content.toDoubleOrNull()?.toInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
