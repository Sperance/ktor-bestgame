package server.serializers

import application.enums.EnumStatBattle
import application.enums.EnumStatBool
import application.enums.EnumStatProfession
import application.enums.EnumStatStock
import application.enums.IntEnumStat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.SerializationException

object IntEnumStatSerializer : KSerializer<IntEnumStat> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IntEnumStat", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: IntEnumStat) {
        val name = when (value) {
            is EnumStatStock -> value.name
            is EnumStatBool -> value.name
            is EnumStatProfession -> value.name
            is EnumStatBattle -> value.name
            else -> throw SerializationException("Unknown IntEnumStat type: ${value::class}")
        }
        encoder.encodeString(name)
    }

    override fun deserialize(decoder: Decoder): IntEnumStat {
        val name = decoder.decodeString()
        // Ищем во всех enum'ах, реализующих IntEnumStat
        return when {
            enumContains<EnumStatStock>(name) -> EnumStatStock.valueOf(name)
            enumContains<EnumStatBool>(name) -> EnumStatBool.valueOf(name)
            enumContains<EnumStatProfession>(name) -> EnumStatProfession.valueOf(name)
            enumContains<EnumStatBattle>(name) -> EnumStatBattle.valueOf(name)
            else -> throw SerializationException("Unknown IntEnumStat value: $name")
        }
    }

    // Вспомогательная функция для проверки наличия значения в enum
    private inline fun <reified T : Enum<T>> enumContains(name: String): Boolean {
        return enumValues<T>().any { it.name == name }
    }
}