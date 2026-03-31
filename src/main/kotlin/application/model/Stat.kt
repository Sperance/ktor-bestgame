package application.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import application.enums.EnumStatKey
import application.enums.EnumStatType

@Serializable(with = CompactStatSerializer::class)
data class Stat(
    val key: EnumStatKey,
    val type: EnumStatType,
    var value: Double
) {
    override fun toString(): String {
        return "Stat(key=$key, type=$type, value=$value)"
    }
}

object CompactStatSerializer : KSerializer<Stat> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Stat", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Stat) {
        encoder.encodeString("${value.key.code}:${value.type.code}:${value.value}")
    }

    override fun deserialize(decoder: Decoder): Stat {
        val stringValue = decoder.decodeString()

        try {
            val parts = stringValue.split(":", limit = 3)
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid Stat format: $stringValue. Expected 'code:type:value'")
            }

            val code = parts[0]
            val type = parts[1].toInt()
            val value = parts[2].toDouble()

            val statKey = EnumStatKey.entries.find { it.code == code } ?: throw IllegalArgumentException("Unknown StatKey code: $code")
            val statType = EnumStatType.entries.find { it.code == type } ?: throw IllegalArgumentException("Unknown StatType code: $type")

            return Stat(statKey, statType, value)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid number format in Stat: $stringValue", e)
        }
    }
}