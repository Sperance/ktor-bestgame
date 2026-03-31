package application.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import application.enums.EnumItem

@Serializable(with = CompactItemStockSerializer::class)
data class ItemStock(
    val type: EnumItem,
    var quantity: Int
) {
    override fun toString(): String {
        return "ItemStock(type=$type, quantity=$quantity)"
    }
}

object CompactItemStockSerializer : KSerializer<ItemStock> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ItemStock", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ItemStock) {
        encoder.encodeString("${value.type.name}:${value.quantity}")
    }

    override fun deserialize(decoder: Decoder): ItemStock {
        val stringValue = decoder.decodeString()

        try {
            val parts = stringValue.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid Stat format: $stringValue. Expected 'type:quantity'")
            }

            val type = parts[0]
            val quantity = parts[1].toInt()

            val statKey = EnumItem.entries.find { it.name == type } ?: throw IllegalArgumentException("Unknown type: $type")

            return ItemStock(statKey, quantity)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid number format in ItemStock: $stringValue", e)
        }
    }
}