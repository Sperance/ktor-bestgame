package application.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import application.model.enums.EnumStatKey

@Serializable(with = CompactParamsStockSerializer::class)
data class ParamsStock(
    var param: EnumStatKey,
    var value: Double,
) {
    fun copy(): ParamsStock {
        return ParamsStock(
            param = this.param,
            value = this.value,
        )
    }
}

object CompactParamsStockSerializer : KSerializer<ParamsStock> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ParamsStock", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ParamsStock) {
        encoder.encodeString("${value.param.code}:${value.value}")
    }

    override fun deserialize(decoder: Decoder): ParamsStock {
        val stringValue = decoder.decodeString()

        try {
            val parts = stringValue.split(":", limit = 2)
            if (parts.size != 2) {
                throw IllegalArgumentException("Invalid ParamsStock format: $stringValue. Expected 'code:value'")
            }

            val code = parts[0]
            val value = parts[1].toDouble()

            val statKey = EnumStatKey.entries.find { it.code == code } ?: throw IllegalArgumentException("Unknown EnumParamsStock code: $code")

            return ParamsStock(statKey, value)
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid number format in ParamsStock: $stringValue", e)
        }
    }
}