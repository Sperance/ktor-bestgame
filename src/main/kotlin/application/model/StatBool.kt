package application.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import application.model.enums.EnumStatBool

@Serializable(with = CompactStatBoolSerializer::class)
data class StatBool(
    val key: EnumStatBool,
    var value: Boolean
) {
    override fun toString(): String {
        return "StatBool(key=$key, value=$value)"
    }
}

object CompactStatBoolSerializer : KSerializer<StatBool> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StatBool", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StatBool) {
        encoder.encodeString("${value.key.code}:${ value.value}")
    }

    override fun deserialize(decoder: Decoder): StatBool {
        val stringValue = decoder.decodeString()

        try {
            val parts = stringValue.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid StatBool format: $stringValue. Expected 'code:value'")
            }

            val code = parts[0]
            val value = parts[1].toBoolean()

            val statKey = EnumStatBool.entries.find { it.code == code } ?: throw IllegalArgumentException("Unknown EnumStatBool code: $code")

            return StatBool(statKey, value)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid number format in StatBool: $stringValue", e)
        }
    }
}
