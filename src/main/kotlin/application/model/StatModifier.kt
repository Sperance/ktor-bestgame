package application.model

import application.enums.EnumModifierCategory
import application.enums.EnumModifierGroup
import application.enums.EnumModifierType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Модификатор характеристики на предмете.
 *
 * Хранится в JSONB как компактная строка: "statId|modType|category|group|tier|value|minValue|maxValue|tags"
 *
 * Примеры:
 *   "1|0|1|LIFE_FLAT_PREFIX|5|45.0|10.0|80.0|life"         — +45 к здоровью (тир 5)
 *   "27|1|2|FIRE_RESIST_SUFFIX|3|35.0|15.0|45.0|resistance|fire" — 35% увеличение сопр.огню (тир 3)
 *
 * @param statId       ID характеристики из таблицы property (→ EnumStatHelper)
 * @param modType      Тип складывания (FLAT_ADD, INCREASED, MORE и т.д.)
 * @param category     Откуда мод: PREFIX, SUFFIX, IMPLICIT, CRAFTED...
 * @param group        Группа мода — на предмете может быть только один мод из группы
 * @param tier         Уровень аффикса: 1 (лучший) .. 8 (худший)
 * @param value        Конкретное значение (может быть отрицательным)
 * @param minValue     Минимум диапазона для данного тира
 * @param maxValue     Максимум диапазона для данного тира
 * @param tags         Теги для фильтрации (fire, cold, attack, defence...)
 */
@Serializable(with = CompactStatModifierSerializer::class)
data class StatModifier(
    val statId: Long,
    val modType: EnumModifierType,
    val category: EnumModifierCategory,
    val group: EnumModifierGroup,
    val tier: Int,
    val value: Double,
    val minValue: Double,
    val maxValue: Double,
    val tags: List<String> = emptyList()
) {
    /** Процент прокатки внутри диапазона (0.0 = minValue, 1.0 = maxValue) */
    fun rollPercent(): Double {
        if (maxValue == minValue) return 1.0
        return ((value - minValue) / (maxValue - minValue)).coerceIn(0.0, 1.0)
    }

    fun displayString(statName: String): String {
        val prefix = when (modType) {
            EnumModifierType.FLAT_ADD -> if (value >= 0) "+${fmt(value)}" else "${fmt(value)}"
            EnumModifierType.INCREASED -> "${fmt(value)}% увеличение"
            EnumModifierType.MORE -> "${fmt(value)}% больше"
            EnumModifierType.LESS -> "${fmt(value)}% меньше"
            EnumModifierType.REDUCED -> "${fmt(value)}% уменьшение"
            EnumModifierType.PENETRATION -> "${fmt(value)}% проникновение"
            EnumModifierType.LEECH -> "${fmt(value)}% вампиризм"
            EnumModifierType.EXTRA_AS -> "${fmt(value)}% дополнительно как"
        }
        val catStr = when (category) {
            EnumModifierCategory.PREFIX -> "[П] "
            EnumModifierCategory.SUFFIX -> "[С] "
            EnumModifierCategory.IMPLICIT -> "[I] "
            EnumModifierCategory.CRAFTED -> "[K] "
            EnumModifierCategory.ENCHANT -> "[З] "
            EnumModifierCategory.CORRUPTED -> "[X] "
            EnumModifierCategory.UNIQUE -> "[U] "
        }
        return "${catStr}T${tier} $prefix к $statName"
    }

    private fun fmt(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
}

object CompactStatModifierSerializer : KSerializer<StatModifier> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StatModifier", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StatModifier) {
        val tagsStr = value.tags.joinToString("~")
        encoder.encodeString(
            "${value.statId}|${value.modType.code}|${value.category.code}" +
                "|${value.group.name}|${value.tier}|${value.value}" +
                "|${value.minValue}|${value.maxValue}|$tagsStr"
        )
    }

    override fun deserialize(decoder: Decoder): StatModifier {
        val s = decoder.decodeString()
        val parts = s.split("|", limit = 9)
        require(parts.size == 9) { "Invalid StatModifier format: $s" }
        return StatModifier(
            statId = parts[0].toLong(),
            modType = EnumModifierType.byCode(parts[1].toInt()),
            category = EnumModifierCategory.byCode(parts[2].toInt()),
            group = EnumModifierGroup.valueOf(parts[3]),
            tier = parts[4].toInt(),
            value = parts[5].toDouble(),
            minValue = parts[6].toDouble(),
            maxValue = parts[7].toDouble(),
            tags = if (parts[8].isEmpty()) emptyList() else parts[8].split("~")
        )
    }
}
