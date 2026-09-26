package application.enums

/**
 * Редкость предмета. Определяет, сколько аффиксов предмет может нести.
 *
 * Лестница построена как в POE: обычный предмет аффиксов не имеет,
 * магический держит по одному префиксу и суффиксу, редкий - по три.
 * UNIQUE и MYTHICAL аффиксов не роллят вовсе: их модификаторы закреплены самим предметом.
 * Мифический предмет (0.53.0) - та же уникалка, только реже и в разы сильнее.
 */
enum class EnumRarity(
    /**
     * Сколько префиксов предмет этой редкости может нести.
     */
    val prefixCount: Int,

    /**
     * Сколько суффиксов предмет этой редкости может нести.
     */
    val suffixCount: Int,

    /**
     * Сколько аффиксов бывает на предмете этой редкости (0.53.0): новый предмет роллит число
     * из диапазона, а сферы не опускают ниже и не поднимают выше.
     */
    val affixes: IntRange = 0..prefixCount + suffixCount,
) {
    COMMON(0, 0),
    UNCOMMON(1, 1, 1..2),
    RARE(3, 3, 4..6),
    UNIQUE(0, 0),
    MYTHICAL(0, 0);

    /** Модификаторы задаёт сам предмет, а не ролл: уникалка и мифический предмет. */
    val fixed: Boolean get() = this == UNIQUE || this == MYTHICAL

    /**
     * Места аффиксов редкости на предмете слота [slot] (0.66.0): самоцвет, как в POE, держит на
     * редком 2+2 и выпадает с 3-4, остальное - как у редкости.
     */
    fun limits(slot: EnumEquipmentType): AffixLimits = when {
        this == RARE && slot == EnumEquipmentType.JEWEL -> AffixLimits(2, 2, 3..4)
        else -> AffixLimits(prefixCount, suffixCount, affixes)
    }
}

/** Сколько префиксов и суффиксов помещается и сколько аффиксов выпадает. */
data class AffixLimits(val prefixes: Int, val suffixes: Int, val affixes: IntRange)
