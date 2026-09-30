package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.table.Tables
import kotlinx.serialization.Serializable
import kotlin.math.max

/*
 * Осквернение (1.4.0): пятна на земле зоны. На каждой карте их несколько, вид и место - по семени
 * забега; пятно под героем и след за ним ([DesecrationRule.trail] секунд) накладывают строки
 * `MAP_HERO_*` поверх строк карты - так бой, начатый на пятне, идёт под ним. Сила растёт с уровнем
 * зоны, награды не даёт; `STOCK_DESECRATION_EFFECT` героя срезает её. Сервер пятен не знает:
 * добычи они не меняют, реплей их не касается.
 */

/** Группа осквернения: стихия, яд, защита, герой. */
@Serializable
enum class DesecrationGroup { ELEMENTAL, POISON, DEFENCE, WEAKNESS }

/** Вид пятна: код (он же ключ `desecration.<code>` локали), группа, строки `MAP_HERO_*` на первом уровне и вес. */
@Serializable
data class DesecrationKind(val code: String, val group: DesecrationGroup, val lines: Map<String, Double>, val weight: Double = 100.0)

/** Правило осквернения из `campaign.json`. */
@Serializable
data class DesecrationRule(
    /** Сколько пятен на зоне, от и до. */
    val count: List<Int> = listOf(2, 5),
    /** Радиус пятна в клетках. */
    val radius: Double = 1.8,
    /** Сколько секунд пятно держится на герое, сошедшем с него. */
    val trail: Double = 4.0,
    /** Прибавка силы за уровень зоны сверх первого, доля. */
    val growth: Double = 0.015,
    val minLevel: Int = 1,
    val kinds: List<DesecrationKind> = emptyList(),
) {
    /** Виды пятен зоны уровня [level] по семени [seed]; ниже [minLevel] - ни одного. */
    fun roll(level: Int, seed: Long): List<DesecrationKind> {
        if (level < minLevel || kinds.isEmpty()) return emptyList()
        val dice = Dice(seed * 2_147_483_647 + 911)
        return List(dice.between(count)) { Tables.draw(kinds, DesecrationKind::weight, dice)!! }
    }

    /** Строки пятна [kind] на зоне уровня [level] для героя, срезающего осквернение на [reduced] процентов. */
    fun lines(kind: DesecrationKind, level: Int, reduced: Double): Map<String, Double> {
        val power = (1 + growth * (level - 1).coerceAtLeast(0)) * max(0.0, 1 - reduced / 100)
        return kind.lines.mapValues { (_, value) -> value * power }
    }

    companion object {
        /** Стат героя, срезающий осквернение. */
        const val GUARD = "STOCK_DESECRATION_EFFECT"
        /** Строки, которые пятно вправе накладывать: всё, что карта делает с героем. */
        val HERO_LINES: Set<String> = MapStat.entries.filter { it.name.startsWith("HERO_") }.mapTo(HashSet()) { it.code }
    }
}
