package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/** Группа характеристики: чей лист её несёт. */
@Serializable
enum class StatGroup { HERO, BOOL, PROFESSION, BATTLE, MAP, ATLAS, FLASK, AURA, POWER }

/** Вид значения: число, флаг или процент без базы (INCREASED складывается в него как есть). */
@Serializable
enum class StatKind { NUMBER, BOOL, PERCENT }

/**
 * Характеристика реестра `stats.json`. [order] - место в порядке подсчёта: источник конверсии
 * объявлен раньше приёмника, поэтому цикл конверсий невыразим и топологическая сортировка не нужна.
 */
@Serializable
data class StatDef(
    val code: String,
    val group: StatGroup,
    val order: Int,
    val kind: StatKind = StatKind.NUMBER,
)

@Serializable
data class StatsFile(val stats: List<StatDef> = emptyList())

/**
 * Характеристики, которые движок читает по имени: их присутствие в реестре проверяется при загрузке.
 * Всё остальное движок несёт как строки и лишь считает.
 */
enum class CoreStat(val code: String) {
    STRENGTH("STOCK_STRENGTH"), AGILITY("STOCK_AGILITY"), INTELLECT("STOCK_INTELLECT"),
    HEALTH("STOCK_HEALTH"), MANA("STOCK_MANA"), LIGHT_RADIUS("STOCK_LIGHT_RADIUS"),
    EXPERIENCE("STOCK_EXPERIENCE"), QUANTITY("STOCK_QUANTITY"), RARITY("STOCK_RARITY"), GOLD("STOCK_GOLD"),
    CHEST_QUANTITY("STOCK_CHEST_QUANTITY"),
    WORK_SPEED("STOCK_WORK_SPEED"), WORK_YIELD("STOCK_WORK_YIELD"), WORK_LUCK("STOCK_WORK_LUCK"),
    WORK_EXPERIENCE("STOCK_WORK_EXPERIENCE"), WORK_FIND("STOCK_WORK_FIND"), WORK_DOUBLE("STOCK_WORK_DOUBLE"), WORK_SAVE("STOCK_WORK_SAVE"),
    WORK_RARITY("STOCK_WORK_RARITY"), WORK_ITEM_LEVEL("STOCK_WORK_ITEM_LEVEL"), WORK_UNIQUE("STOCK_WORK_UNIQUE"),
    ATTACK_PHYSICAL("STOCK_ATTACK_PHYSICAL"), ATTACK_MAGICAL("STOCK_ATTACK_MAGICAL"),
    ATTACK_FIRE("STOCK_ATTACK_FIRE"), ATTACK_COLD("STOCK_ATTACK_COLD"), ATTACK_LIGHTNING("STOCK_ATTACK_LIGHTNING"), ATTACK_CHAOS("STOCK_ATTACK_CHAOS"),
    DAMAGE("STOCK_DAMAGE"),
    CRITICAL_CHANCE("STOCK_CRITICAL_CHANCE"), CRITICAL_MULTIPLIER("STOCK_CRITICAL_MULTIPLIER"), CRITICAL_DAMAGE("STOCK_CRITICAL_DAMAGE"),
    SPELL_CRITICAL_CHANCE("STOCK_SPELL_CRITICAL_CHANCE"), SPELL_CRITICAL_MULTIPLIER("STOCK_SPELL_CRITICAL_MULTIPLIER"),
    BORROW_SKILLS("STOCK_BORROW_SKILLS"),
    MAP_QUANTITY("MAP_QUANTITY"), MAP_RARITY("MAP_RARITY"), MAP_EXPERIENCE("MAP_EXPERIENCE"), MAP_CHESTS("MAP_CHESTS"),
    MAP_GOLD("MAP_GOLD"), MAP_FOUNTAINS("MAP_FOUNTAINS"), MAP_BOSS_POWER("MAP_BOSS_POWER"), MAP_CRYSTALS("MAP_CRYSTALS"),
    MAP_BOOKS("MAP_BOOKS"), MAP_ABYSS_CRACKS("MAP_ABYSS_CRACKS"), MAP_ABYSS_DEPTH("MAP_ABYSS_DEPTH"),
    MAP_ABYSS_HOARD("MAP_ABYSS_HOARD"), MAP_ABYSS_UNIQUE("MAP_ABYSS_UNIQUE"), MAP_ABYSS_ORBS("MAP_ABYSS_ORBS"),
    MAP_ABYSS_RARE("MAP_ABYSS_RARE"),
}

/**
 * Урон вообще (1.57.0): увеличение `STOCK_DAMAGE` - обычное увеличение урона каждого вида удара, оно складывается с его
 * увеличениями, как в PoE; прибавка и «больше» `STOCK_DAMAGE` - отдельный множитель боя «больше урона».
 */
object GenericDamage {
    val STAT: String = CoreStat.DAMAGE.code
    /** Виды урона удара героя, на которые ложится увеличение урона вообще. */
    val HITS: List<String> = listOf(CoreStat.ATTACK_PHYSICAL, CoreStat.ATTACK_FIRE, CoreStat.ATTACK_COLD, CoreStat.ATTACK_LIGHTNING, CoreStat.ATTACK_CHAOS).map { it.code }

    fun spreads(stat: String, op: Op): Boolean = stat == STAT && op == Op.INCREASED

    /** Куда ложится операция [op] над [stat]: увеличение урона вообще - в каждый вид удара, остальное - в свой стат. */
    fun targets(stat: String, op: Op): List<String> = if (spreads(stat, op)) HITS else listOf(stat)
}

/**
 * Общая характеристика (1.58.0): модификатор «ко всем X» - одна строка и один стат, как увеличение урона вообще ([GenericDamage]):
 * лист раскладывает любую его операцию на каждого из [members], и дальше она считается там.
 */
enum class GenericStat(val code: String, val members: List<String>) {
    ALL_ATTRIBUTES("STOCK_ALL_ATTRIBUTES", listOf(CoreStat.STRENGTH, CoreStat.AGILITY, CoreStat.INTELLECT).map { it.code }),
    /** Все сопротивления - стихиям (их общий `STOCK_RESIST_ALL`, его читает бой) и хаосу. */
    ALL_RESISTANCES("STOCK_ALL_RESISTANCES", listOf("STOCK_RESIST_ALL", "STOCK_RESIST_CHAOS")),
    ELEMENTAL_DAMAGE("STOCK_ELEMENTAL_DAMAGE", listOf(CoreStat.ATTACK_FIRE, CoreStat.ATTACK_COLD, CoreStat.ATTACK_LIGHTNING).map { it.code }),
    AVOID_ELEMENTAL_AILMENTS("STOCK_AVOID_ELEMENTAL_AILMENTS", listOf("STOCK_AVOID_IGNITE", "STOCK_AVOID_CHILL", "STOCK_AVOID_FREEZE", "STOCK_AVOID_SHOCK"));

    companion object {
        private val byCode: Map<String, GenericStat> = entries.associateBy { it.code }
        fun of(stat: String): GenericStat? = byCode[stat]
    }
}

/** Куда лист кладёт операцию [op] над [stat]: общая характеристика - в своих членов, увеличение урона вообще - в каждый вид удара, остальное - в свой стат. */
object StatSpread {
    fun targets(stat: String, op: Op): List<String> = GenericStat.of(stat)?.members ?: GenericDamage.targets(stat, op)
}

/** Реестр характеристик: порядок подсчёта, проценты, группы. */
class StatRegistry(val stats: List<StatDef>) {
    private val byCode: Map<String, StatDef> = stats.associateBy { it.code }
    private val orders: Map<String, Int> = stats.associate { it.code to it.order }
    val percent: Set<String> = stats.filter { it.kind == StatKind.PERCENT }.mapTo(HashSet()) { it.code }

    operator fun contains(code: String): Boolean = code in byCode
    operator fun get(code: String): StatDef? = byCode[code]
    fun order(code: String): Int = orders[code] ?: Int.MAX_VALUE
    fun isPercent(code: String): Boolean = code in percent
    fun ofGroup(group: StatGroup): List<StatDef> = stats.filter { it.group == group }
    /** Имена характеристик группы [group] в порядке подсчёта. */
    fun codes(group: StatGroup): List<String> = ofGroup(group).sortedBy { it.order }.map { it.code }

    fun validate() {
        if (byCode.size != stats.size) fail("stats: duplicate codes")
        CoreStat.entries.forEach { if (it.code !in byCode) fail("stats: engine stat ${it.code} is missing") }
        MapStat.entries.forEach { if (it.code !in byCode) fail("stats: map stat ${it.code} is missing") }
        AtlasStat.entries.forEach { if (it.code !in byCode) fail("stats: atlas stat ${it.code} is missing") }
        GenericStat.entries.forEach { generic -> (generic.members + generic.code).forEach { if (it !in byCode) fail("stats: generic ${generic.code} names missing $it") } }
        if (stats.any { it.code.isBlank() }) fail("stats: blank code")
    }

    /** Порядок группы флагов (`BOOL_*`) - имена недугов без префикса, как их зовёт бой. */
    fun ailments(): Set<String> = codes(StatGroup.BOOL).mapTo(HashSet()) { it.removePrefix("BOOL_") }
}
