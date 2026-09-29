package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.sheet.StatContribution
import kotlinx.serialization.Serializable

/**
 * Бафф боя (1.34.0), как в PoE: [chance] - стат героя с шансом получить его (при убийстве, у Укрепления - при ударе
 * оружием), [always] - стат бойца, что носит его постоянно (моды монстров и карт), [icon] - стат, чьим знаком он рисуется.
 */
@Serializable
enum class BuffKind(val chance: String, val icon: String, val onHit: Boolean = false) {
    ONSLAUGHT("STOCK_ONSLAUGHT_ON_KILL", "STOCK_ATTACK_SPEED"),
    UNHOLY_MIGHT("STOCK_UNHOLY_MIGHT_ON_KILL", "STOCK_ATTACK_CHAOS"),
    ARCANE_SURGE("STOCK_ARCANE_SURGE_ON_KILL", "STOCK_CAST_SPEED"),
    FORTIFY("STOCK_FORTIFY_ON_HIT", "STOCK_ARMOR", onHit = true);

    val always: String get() = "STOCK_${name}_ALWAYS"
    /** Источник бафф-эффекта на бойце: один на вид, новый заменяет прежний. */
    val source: String get() = "$SOURCE$name"

    val condition: Condition? get() = when (this) {
        ONSLAUGHT -> Condition.ONSLAUGHT
        FORTIFY -> Condition.FORTIFIED
        else -> null
    }

    companion object {
        const val SOURCE = "BUFF_"
        /** Насколько дольше идут баффы героя, в процентах. */
        const val DURATION = "STOCK_BUFF_DURATION"
        fun of(source: String): BuffKind? = if (source.startsWith(SOURCE)) entries.firstOrNull { it.source == source } else null
    }
}

/** Что даёт бафф и сколько секунд. */
@Serializable
data class BuffRule(val duration: Double = 4.0, val lines: List<StatContribution> = emptyList())

/** Правила баффов (1.34.0): по виду; без записи в контенте - значения PoE. */
@Serializable
data class BuffRules(
    val onslaught: BuffRule = BuffRule(4.0, listOf(
        StatContribution("STOCK_ATTACK_SPEED", Op.INCREASED, 20.0), StatContribution("STOCK_CAST_SPEED", Op.ADD, 20.0),
        StatContribution("STOCK_MOVEMENT_SPEED", Op.ADD, 20.0))),
    val unholyMight: BuffRule = BuffRule(4.0, listOf(StatContribution("STOCK_PHYSICAL_AS_EXTRA_CHAOS", Op.ADD, 30.0))),
    val arcaneSurge: BuffRule = BuffRule(4.0, listOf(
        StatContribution("STOCK_CAST_SPEED", Op.ADD, 20.0), StatContribution("STOCK_MANA_REGEN", Op.ADD, 30.0))),
    val fortify: BuffRule = BuffRule(4.0, listOf(StatContribution("STOCK_HIT_TAKEN", Op.ADD, -20.0))),
) {
    operator fun get(kind: BuffKind): BuffRule = when (kind) {
        BuffKind.ONSLAUGHT -> onslaught
        BuffKind.UNHOLY_MIGHT -> unholyMight
        BuffKind.ARCANE_SURGE -> arcaneSurge
        BuffKind.FORTIFY -> fortify
    }
}

/**
 * Меткость (1.34.0), формула PoE: шанс попасть `hitScale × A / (A + (E / evasionDivisor)^evasionPower)`, не ниже [minHit].
 * Меткость бойца - [base] + [perLevel] за уровень + [perDexterity] за ловкость + стат `STOCK_ACCURACY`.
 */
@Serializable
data class AccuracyRule(
    val base: Double = 20.0,
    val perLevel: Double = 3.0,
    val perDexterity: Double = 0.5,
    val hitScale: Double = 1.25,
    val evasionDivisor: Double = 5.0,
    val evasionPower: Double = 0.9,
    val minHit: Double = 0.05,
) {
    /** Доля ударов меткости [accuracy], что уходят от уклонения [evasion]. */
    fun evaded(accuracy: Double, evasion: Double): Double {
        if (evasion <= 0) return 0.0
        val hit = hitScale * accuracy / (accuracy + Math.pow(evasion / evasionDivisor, evasionPower))
        return 1 - hit.coerceIn(minHit, 1.0)
    }
}

/** Подавление чар и отклонение (1.34.0): потолки шансов и сколько урона удара они снимают, в процентах. */
@Serializable
data class DefenceRule(
    val suppressionCap: Double = 75.0,
    val suppressed: Double = 40.0,
    val deflectionCap: Double = 50.0,
    val deflected: Double = 40.0,
    /** Возврат здоровья идёт столько секунд. */
    val recoup: Double = 4.0,
)
