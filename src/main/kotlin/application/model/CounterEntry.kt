package application.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Запись счётчика: ключ + накопленное значение.
 * Компактная сериализация: "K1:150" (код счётчика: значение)
 */
@Serializable(with = CompactCounterEntrySerializer::class)
data class CounterEntry(
    val key: Long,
    var value: Long
)

object CompactCounterEntrySerializer : KSerializer<CounterEntry> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CounterEntry", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CounterEntry) {
        encoder.encodeString("${value.key}:${value.value}")
    }

    override fun deserialize(decoder: Decoder): CounterEntry {
        val str = decoder.decodeString()
        val parts = str.split(":", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Invalid CounterEntry: $str")

        val key = parts[0].toLongOrNull()
            ?: throw IllegalArgumentException("Unknown counter code: ${parts[0]}")
        val value = parts[1].toLongOrNull()
            ?: throw IllegalArgumentException("Invalid number in CounterEntry: $str")

        return CounterEntry(key, value)
    }
}
