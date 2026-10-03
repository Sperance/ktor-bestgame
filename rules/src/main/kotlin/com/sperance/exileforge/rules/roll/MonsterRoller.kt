package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.BehaviourRule
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Monster
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.MonsterTrait
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.RarityRule
import com.sperance.exileforge.rules.content.Zone
import com.sperance.exileforge.rules.content.tenths
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** Одно изменение характеристики монстра: диапазон тира на уровне карты, само значение бросается при встрече. */
@Serializable
data class MonsterEffect(val stat: String, val op: Op, val value: Double, val max: Double = value)

/** Модификатор монстра на уровне зоны: описание, вес в таблицах зоны, редкость, с которой открыт, тир и его диапазоны. */
@Serializable
data class MonsterMod(val code: String, val weight: Int, val minLevel: Int, val minRarity: MonsterRarity, val effects: List<MonsterEffect>, val tier: Int)

/** Монстр как он стоит на карте: редкость, выпавшие модификаторы (значения уже брошены), итоговые характеристики. */
data class RolledMonster(
    val code: String,
    val form: String,
    val rarity: MonsterRarity,
    val modifiers: List<MonsterMod>,
    val stats: Map<String, Double>,
    val behaviour: BehaviourRule,
    val skills: List<String> = emptyList(),
    val mapBuffs: List<MonsterEffect> = emptyList(),
    /** Уровень монстра (1.69.0): по нему его статы, опыт и уровень добычи; 0 - уровень зоны. */
    val level: Int = 0,
    /** Свойства монстра (1.69.0): форма, затем тип; их строки уже в [stats], отклики читает бой. */
    val traits: List<String> = emptyList(),
)

/** Босс или страж на уровне зоны: сигнатурные строки и таблица, из которой при каждой встрече добираются ещё. */
data class GuardianView(val monster: Monster, val level: Int, val stats: Map<String, Double>, val signature: List<MonsterMod>, val pool: List<MonsterMod>, val rolls: List<Int>)

/**
 * Монстры зоны: рост характеристик по уровню, модификаторы таблиц зоны на её тире, ролл редкости и
 * строк при встрече, боссы и стражи. Одно правило на клиент, что дерётся, и сервер, что проверяет.
 */
class MonsterRoller(private val index: ContentIndex) {
    private val campaign get() = index.campaign

    /** Характеристика на уровне: растёт только названная в `growth`, степенью со спадом. */
    fun scale(stat: String, value: Double, level: Int): Double {
        val factor = campaign.growth[stat] ?: return value
        return Math.round(value * factor.pow(campaign.growthTaper.steps(level)) * 100.0) / 100.0
    }

    /** Характеристики монстра на уровне; урон крита (1.57.0) - с базы боя, чтобы его увеличения не ложились на ноль. */
    fun stats(monster: Monster, level: Int): Map<String, Double> = (campaign.combat.critical.fighterBase + campaign.defaults + monster.stats).mapValues { (stat, value) -> scale(stat, value, level) }

    /** Описание на тире уровня [tierLevel], плоские прибавки поднятые по росту до [level]. */
    fun raise(def: ModifierDef, weight: Int, level: Int, tierLevel: Int = level): MonsterMod {
        val (number, tier) = def.bestTierAt(tierLevel) ?: (1 to com.sperance.exileforge.rules.content.Tier(1, 0, def.effects.map { listOf(0.0, 0.0) }))
        return MonsterMod(
            def.code,
            weight,
            def.tiers.minOfOrNull { it.level } ?: 1,
            def.minRarity ?: MonsterRarity.MAGIC,
            def.effects.mapIndexed { i, effect ->
                val (min, max) = tier.values[i]
                if (effect.op == Op.ADD) MonsterEffect(effect.stat, effect.op, scale(effect.stat, min, level), scale(effect.stat, max, level)) else MonsterEffect(effect.stat, effect.op, min, max)
            },
            number,
        )
    }

    /** Модификаторы таблиц [tags] на уровне зоны [level]. */
    fun pool(tags: List<String>, level: Int, tierLevel: Int = level): List<MonsterMod> = index.modifierPool(tags).filter { it.value.monster }.map { (def, weight) -> raise(def, weight, level, tierLevel) }

    fun zonePool(zone: Zone): List<MonsterMod> = pool(zone.tables, zone.level)

    /**
     * Босс, страж порчи или вожак Бездны на уровне [level]: сигнатуры на тире `level + tierReach`, таблица на тире уровня.
     * Дополнительные строки таблицы - только открытые уровнем зоны, как у обычных монстров ([draw]): тир сверх уровня - удел сигнатур.
     */
    fun guardian(code: String, level: Int, tables: List<String> = campaign.bosses.modifiers, rolls: List<Int> = campaign.bosses.rollsAt(level), tierReach: Int = campaign.bosses.tierReach): GuardianView {
        val monster = index.monster(code) ?: throw IllegalArgumentException("unknown monster $code")
        val signature = monster.fixed.mapNotNull { fixed -> index.modifier(fixed)?.let { raise(it, index.tables.weight(fixed, tables), level, level + tierReach) } }
        return GuardianView(monster, level, stats(monster, level), signature, pool(tables, level).filter { it.code !in monster.fixed && it.minLevel <= level }, rolls)
    }

    fun rarityRule(dice: Dice): RarityRule {
        val rarity = Tables.value<MonsterRarity>(index.tables, campaign.rarityTable, dice) ?: MonsterRarity.NORMAL
        return campaign.rarity(rarity)
    }

    /** Редкость и модификаторы монстра [code] зоны при встрече; [extraRareMods] - лишние строки редкого от атласа. */
    fun roll(
        zone: Zone,
        code: String,
        pool: List<MonsterMod>,
        dice: Dice,
        extraRareMods: Int = 0,
        rule: RarityRule = rarityRule(dice),
        level: Int = zone.level,
    ): RolledMonster {
        val monster = index.monster(code) ?: throw IllegalArgumentException("unknown monster $code")
        var count = dice.between(rule.modifiers)
        if (rule.rarity == MonsterRarity.RARE && count > 0) count += extraRareMods
        val picked = draw(pool, level, rule, count, dice)
        return build(monster, level, rule, picked, dice)
    }

    /** Монстр [code] редкости [rule] с уже вытянутыми строками - для стражей кристаллов (редкий + строки эссенций) и волн Бездны. */
    fun build(monster: Monster, level: Int, rule: RarityRule, modifiers: List<MonsterMod>, dice: Dice, extra: List<MonsterEffect> = emptyList(), skills: List<String> = monster.skills): RolledMonster {
        val traits = campaign.traits.of(monster)
        val stats = fold(stats(monster, level), rarityEffects(rule) + modifiers.flatMap { it.effects } + extra + traitEffects(traits, rule.rarity))
        val own = (skills + traits.mapNotNull { it.skill }).distinct()
        return RolledMonster(monster.code, monster.form, rule.rarity, modifiers, stats, campaign.behaviourOf(monster), own, extra, level, traits.map { it.code })
    }

    /** Строки свойств (1.69.0) на силе редкости [rarity]; навык свойства приносит ману, если своей у монстра нет. */
    fun traitEffects(traits: List<MonsterTrait>, rarity: MonsterRarity): List<MonsterEffect> {
        val power = campaign.traits.power(rarity)
        return traits.flatMap { trait ->
            trait.lines.map { MonsterEffect(it.stat, it.op, campaign.traits.scaled(it, power)) } +
                listOfNotNull(trait.skill?.let { MonsterEffect(MANA, Op.ADD, campaign.traits.skillMana) })
        }
    }

    /** [count] строк таблицы для редкости [rule]: тир открывает строки по `minRarity`, без повторов, значения брошены с силой редкости. */
    fun draw(pool: List<MonsterMod>, level: Int, rule: RarityRule, count: Int, dice: Dice): List<MonsterMod> {
        val open = pool.filter { it.minLevel <= level && it.minRarity <= rule.rarity }.toMutableList()
        return List(count) { Tables.draw(open.map { Weighted(it, it.weight) }, dice)?.also { open.remove(it) } }.filterNotNull().map { rolled(it, rule.modifierPower, dice) }
    }

    /** Значения строки внутри её тира: одна доля на все эффекты, умноженные на силу редкости. */
    fun rolled(mod: MonsterMod, power: Double, dice: Dice): MonsterMod {
        val share = dice.nextDouble()
        return mod.copy(
            effects = mod.effects.map { effect ->
                val value = tenths((effect.value + (effect.max - effect.value) * share) * power)
                effect.copy(value = value, max = value)
            },
        )
    }

    /** Босс зоны при встрече: уникальная редкость, сигнатуры и `rolls` строк его таблицы; [extra] - что карта делает с боссом. */
    fun boss(view: GuardianView, dice: Dice, extra: List<MonsterEffect> = emptyList()): RolledMonster {
        val rule = campaign.rarity(MonsterRarity.UNIQUE)
        val signature = view.signature.map { rolled(it, rule.modifierPower, dice) }
        val count = dice.between(view.rolls).coerceAtMost(view.pool.size)
        val pool = view.pool.toMutableList()
        val drawn = List(count) { Tables.draw(pool.map { Weighted(it, it.weight) }, dice)?.also { pool.remove(it) } }.filterNotNull().map { rolled(it, rule.modifierPower, dice) }
        val boss = build(view.monster, view.level, rule, signature + drawn, dice, extra)
        val block = boss.stats[BLOCK] ?: return boss
        return if (block <= campaign.bosses.blockCap) boss else boss.copy(stats = boss.stats + (BLOCK to campaign.bosses.blockCap))
    }

    /** Эффекты редкости: закреплённые строки и «больше» ко всем растущим характеристикам. */
    fun rarityEffects(rule: RarityRule): List<MonsterEffect> {
        val lines = rule.lines.flatMap { line -> index.modifier(line.code)?.effects?.mapIndexedNotNull { i, effect -> line.values.getOrNull(i)?.let { MonsterEffect(effect.stat, effect.op, it) } }.orEmpty() }
        return lines + if (rule.statScale > 0) campaign.growth.keys.map { MonsterEffect(it, Op.MORE, rule.statScale) } else emptyList()
    }

    /** Характеристики после строк: `(база + ΣADD) × (1 + ΣINCREASED/100) × Π(1 + MORE/100)`, SET последним. */
    fun fold(base: Map<String, Double>, effects: List<MonsterEffect>): Map<String, Double> {
        val byStat = effects.groupBy { it.stat }
        return (base.keys + byStat.keys).associateWith { stat ->
            val own = byStat[stat].orEmpty()
            own.lastOrNull { it.op == Op.SET }?.value ?: run {
                val added = (base[stat] ?: 0.0) + own.filter { it.op == Op.ADD }.sumOf { it.value }
                val increased = 1 + own.filter { it.op == Op.INCREASED }.sumOf { it.value } / 100
                val more = own.filter { it.op == Op.MORE }.fold(1.0) { product, it -> product * (1 + it.value / 100) }
                added * increased * more
            }
        }
    }

    private companion object {
        const val BLOCK = "STOCK_BLOCK_CHANCE"
        const val MANA = "STOCK_MANA"
    }
}
