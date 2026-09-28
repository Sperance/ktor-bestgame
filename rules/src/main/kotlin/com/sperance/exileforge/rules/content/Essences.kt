package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

@Serializable data class EssenceTier(val code: String, val level: Int, val rerollsRare: Boolean = false)
@Serializable data class EssenceKind(val code: String, val weapon: String, val armour: String, val jewellery: String, val monster: String)

/** Кристаллы зоны: окно как у сундуков; [vaal] - таблица исходов сферы Ваал, [modifiers] - таблицы строк стражей. */
@Serializable
data class CrystalRule(
    val count: List<Int>, val refreshHours: Double, val essences: List<Int>, val lowerChance: Double,
    val modifiers: List<String>, val bookChance: Double, val vaal: String = "vaal:crystal", val stronger: Double,
    /** Уникалка со стража кристалла (1.19.0): шанс и собственный пул механики. */
    val uniqueChance: Double = 0.0, val uniqueTables: List<String> = emptyList(),
)

@Serializable data class CondenseRule(val inputs: Int, val levels: List<Int>, val seconds: Int)

/** Файл `essences.json`. */
@Serializable
data class EssenceBook(val tiers: List<EssenceTier>, val kinds: List<EssenceKind>, val specials: List<EssenceKind>, val crystals: CrystalRule, val condense: CondenseRule) {
    val essences: Map<String, Essence> by lazy {
        (kinds.flatMap { kind -> (1..tiers.size).map { Essence(kind, it, false) } } + specials.map { Essence(it, 0, true) }).associateBy { it.code }
    }

    fun tierOf(level: Int): Int = tiers.indexOfLast { it.level <= level }.coerceAtLeast(0) + 1

    fun validate(modifier: (String) -> ModifierDef?) {
        if (tiers.isEmpty() || tiers.zipWithNext().any { (a, b) -> a.level >= b.level }) fail("essences: tiers")
        val codes = (kinds + specials).map { it.code }
        if (codes.toSet().size != codes.size) fail("essences: kinds")
        (kinds + specials).forEach { kind ->
            listOf(kind.weapon, kind.armour, kind.jewellery, kind.monster).forEach { if (modifier(it) == null) fail("essences: modifier $it of ${kind.code}") }
        }
        val rule = crystals
        if (rule.count.size != 2 || rule.count[0] > rule.count[1] || rule.essences.size != 2 || rule.essences[0] < 1 || rule.essences[0] > rule.essences[1]) fail("essences: crystals")
        if (rule.modifiers.isEmpty()) fail("essences: crystal modifiers")
        if (condense.levels.size != tiers.size - 1 || condense.inputs < 2) fail("essences: condense")
    }

    companion object {
        const val PREFIX = "ESSENCE_"
        const val VAAL_UPGRADE = "UPGRADE"
        const val VAAL_SPECIAL = "SPECIAL"
        const val VAAL_STRONGER = "STRONGER"
        fun code(kind: String, tier: Int, special: Boolean): String = if (special) "$PREFIX$kind" else "$PREFIX${kind}_$tier"
    }
}

/** Эссенция в сумке: вид, ступень (у особой 0) и код предмета. */
data class Essence(val kind: EssenceKind, val tier: Int, val special: Boolean) {
    val code: String get() = EssenceBook.code(kind.code, tier, special)

    /** Код гарантированной строки на вещи слота [slot]; null - вещь эссенцию не берёт. */
    fun guarantee(slot: Slot): String? = when (slot) {
        Slot.WEAPON_1H, Slot.WEAPON_2H, Slot.QUIVER -> kind.weapon
        Slot.HELMET, Slot.BODY, Slot.GLOVES, Slot.BOOTS, Slot.SHIELD, Slot.WINGS -> kind.armour
        Slot.RING, Slot.RING_2, Slot.AMULET, Slot.BELT -> kind.jewellery
        else -> null
    }
}
