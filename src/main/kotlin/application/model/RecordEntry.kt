package application.model

import application.enums.EnumRecord
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Запись рекорда: ключ + лучшее значение.
 * Компактная сериализация: "M1:9999" (код рекорда : значение)
 */
@Serializable(with = CompactRecordEntrySerializer::class)
data class RecordEntry(
    val key: EnumRecord,
    var value: Int
)

object CompactRecordEntrySerializer : KSerializer<RecordEntry> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("RecordEntry", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RecordEntry) {
        encoder.encodeString("${value.key.code}:${value.value}")
    }

    override fun deserialize(decoder: Decoder): RecordEntry {
        val str = decoder.decodeString()
        val parts = str.split(":", limit = 2)
        if (parts.size != 2) throw IllegalArgumentException("Invalid RecordEntry: $str")

        val key = EnumRecord.entries.find { it.code == parts[0] }
            ?: throw IllegalArgumentException("Unknown record code: ${parts[0]}")
        val value = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("Invalid number in RecordEntry: $str")

        return RecordEntry(key, value)
    }
}
