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
 * Знамение (1.35.0, как в PoE2): расходник, что меняет сферу из [orbs], потраченную вместе с ним.
 * Левое (SINISTRAL) касается только префиксов, правое (DEXTRAL) - только суффиксов. Выбора (1.65.0) - алхимия и возвышение
 * предлагают варианты строки на выбор; тира - божественная поднимает тир. Катализатор (1.65.0) - знамение сферы качества:
 * она поднимает качество его вида [catalyst], а не базы; его код предмета - имя без префикса `OMEN_`.
 */
@Serializable
enum class Omen(val orbs: Set<Orb>, val catalyst: Catalyst? = null) {
    SINISTRAL_CHAOS(Orb.CHAOS_ORB), DEXTRAL_CHAOS(Orb.CHAOS_ORB),
    SINISTRAL_EXALTATION(Orb.EXALTED_ORB), DEXTRAL_EXALTATION(Orb.EXALTED_ORB), GREATER_EXALTATION(Orb.EXALTED_ORB),
    SINISTRAL_CORONATION(Orb.REGAL_ORB), DEXTRAL_CORONATION(Orb.REGAL_ORB),
    SINISTRAL_ANNULMENT(Orb.ORB_OF_ANNULMENT), DEXTRAL_ANNULMENT(Orb.ORB_OF_ANNULMENT), LIGHT(Orb.ORB_OF_ANNULMENT),
    CORRUPTION(Orb.VAAL_ORB),
    CHOICE(Orb.ORB_OF_ALCHEMY, Orb.EXALTED_ORB), TIER(Orb.DIVINE_ORB),
    CATALYST_LIFE(Catalyst.LIFE), CATALYST_DEFENCE(Catalyst.DEFENCE), CATALYST_ELEMENTAL(Catalyst.ELEMENTAL), CATALYST_PHYSICAL(Catalyst.PHYSICAL),
    CATALYST_CHAOS(Catalyst.CHAOS), CATALYST_SPEED(Catalyst.SPEED), CATALYST_ATTRIBUTE(Catalyst.ATTRIBUTE), CATALYST_CASTER(Catalyst.CASTER);

    constructor(vararg orbs: Orb) : this(orbs.toSet())
    constructor(catalyst: Catalyst) : this(setOf(Orb.QUALITY_ORB), catalyst)

    /** Код предмета сумки: `OMEN_<имя>`, у катализатора - его имя. */
    val code: String get() = if (catalyst != null) name else "$PREFIX$name"

    /** Меняет ли знамение сферу [orb]. */
    fun fits(orb: Orb): Boolean = orb in orbs

    /** Сторона аффиксов, которой знамение ограничивает сферу; null - не ограничивает. */
    val side: Source? get() = when {
        name.startsWith("SINISTRAL") -> Source.PREFIX
        name.startsWith("DEXTRAL") -> Source.SUFFIX
        else -> null
    }

    companion object {
        const val PREFIX = "OMEN_"
        private val byCode = entries.associateBy { it.code }
        fun of(code: String): Omen? = byCode[code]
    }
}

/**
 * Качество (1.35.0; 1.65.0 - одна сфера качества): потолок и шаг за сферу по редкости копии. Без катализатора - база и локальная
 * защита или физический урон оружия и брони, качество фляги, строки труда инструмента, количество добычи карты, строки питомца;
 * с катализатором - модификаторы его вида на любой копии, кроме фляги, карты и инструмента.
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
