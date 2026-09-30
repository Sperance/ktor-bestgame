package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/**
 * Катализатор (1.35.0): качество копии усиливает её модификаторы с тегами вида, а не её базу. Новый вид
 * катализатора на копии сбрасывает качество прежнего.
 */
@Serializable
enum class Catalyst(val tags: Set<String>) {
    LIFE(setOf("life", "regen", "leech")),
    DEFENCE(setOf("defences", "armour", "evasion", "energy_shield", "block")),
    ELEMENTAL(setOf("elemental", "fire", "cold", "lightning", "resistance")),
    PHYSICAL(setOf("physical")),
    CHAOS(setOf("chaos")),
    SPEED(setOf("speed")),
    ATTRIBUTE(setOf("attribute")),
    CASTER(setOf("caster", "mana"));

    fun covers(tags: Collection<String>): Boolean = tags.any { it in this.tags }
}

/**
 * Знамение (1.35.0, как в PoE2): расходник, что меняет одну сферу [orb], потраченную вместе с ним.
 * Левое (SINISTRAL) касается только префиксов, правое (DEXTRAL) - только суффиксов.
 */
@Serializable
enum class Omen(val orb: Orb) {
    SINISTRAL_CHAOS(Orb.CHAOS_ORB), DEXTRAL_CHAOS(Orb.CHAOS_ORB),
    SINISTRAL_EXALTATION(Orb.EXALTED_ORB), DEXTRAL_EXALTATION(Orb.EXALTED_ORB), GREATER_EXALTATION(Orb.EXALTED_ORB),
    SINISTRAL_CORONATION(Orb.REGAL_ORB), DEXTRAL_CORONATION(Orb.REGAL_ORB),
    SINISTRAL_ANNULMENT(Orb.ORB_OF_ANNULMENT), DEXTRAL_ANNULMENT(Orb.ORB_OF_ANNULMENT), LIGHT(Orb.ORB_OF_ANNULMENT),
    CORRUPTION(Orb.VAAL_ORB);

    /** Код предмета сумки: `OMEN_<имя>`. */
    val code: String get() = "$PREFIX$name"

    /** Сторона аффиксов, которой знамение ограничивает сферу; null - не ограничивает. */
    val side: Source? get() = when {
        name.startsWith("SINISTRAL") -> Source.PREFIX
        name.startsWith("DEXTRAL") -> Source.SUFFIX
        else -> null
    }

    companion object {
        const val PREFIX = "OMEN_"
        fun of(code: String): Omen? = if (code.startsWith(PREFIX)) entries.firstOrNull { it.code == code } else null
    }
}

/**
 * Качество (1.35.0): потолок и шаг за сферу по редкости копии - оселок оружию, обрезки брони броне, катализатор
 * любой копии, кроме фляги и карты. Качество без катализатора усиливает базу и локальную защиту или физический урон.
 */
@Serializable
data class QualityRules(
    val max: Int = 20,
    val steps: Map<Rarity, Int> = mapOf(Rarity.COMMON to 5, Rarity.MAGIC to 2, Rarity.RARE to 1, Rarity.UNIQUE to 1, Rarity.MYTHICAL to 1),
    /** Сколько вариантов предлагает сфера раскрытия. */
    val unveilChoices: Int = 3,
) {
    fun step(rarity: Rarity): Int = steps[rarity] ?: 1

    companion object {
        /** Что поднимает качество без катализатора: база и локальные строки этих статов. */
        val BASE_STATS = setOf("STOCK_ARMOR", "STOCK_EVASION", "STOCK_ENERGY_SHIELD", "STOCK_ATTACK_PHYSICAL")
    }
}
