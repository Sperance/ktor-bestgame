package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.MapStat
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Monster
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.RarityRule
import com.sperance.exileforge.rules.content.DeathRule
import com.sperance.exileforge.rules.table.Ref
import com.sperance.exileforge.rules.table.TableKind
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted
import kotlinx.serialization.Serializable
import kotlin.math.pow

/** Что выпало по таблице, до того как легло герою: золото, стопки по коду предмета и таблицы, из которых тянуть вещи. */
data class RolledLoot(val gold: Long, val items: Map<String, Long>, val equipment: List<List<String>>)

/**
 * Карта, с которой герой вошёл в зону: её строки, сложенные по характеристикам, и что они прибавляют
 * добыче в процентах к количеству, редкости и опыту; [itemRarity] - редкость самой карты.
 */
@Serializable
data class ActiveMap(
    val mapCode: String,
    val effects: Map<String, Double> = emptyMap(),
    val quantity: Double = 0.0,
    val rarity: Double = 0.0,
    val experience: Double = 0.0,
    val itemRarity: Rarity = Rarity.COMMON,
)

/** Добыча, опыт, карты и смерть - правила без состояния над [ContentIndex] и [Dice]. */
class LootRoller(private val index: ContentIndex) {
    private val rules get() = index.rules.loot
    private val campaign get() = index.campaign

    /** Опыт за монстра: база, уровень зоны в степени, редкость и бонус героя в процентах. */
    fun experience(monster: Monster, level: Int, rarity: RarityRule, bonus: Double): Double =
        Math.round(monster.experience * level.toDouble().pow(rules.experiencePower) * rarity.experience * (1 + bonus / 100)).toDouble()

    /**
     * Броски по таблице добычи [tag]: количество - множитель шанса каждой строки, шанс больше единицы -
     * гарантированные выпадения и остаток шансом; золото растёт со своим спадом или спадом роста монстров.
     */
    fun roll(tag: String, level: Int, rarity: RarityRule, quantity: Double, goldBonus: Double, dice: Dice, goldShare: Double = 1.0, orbShare: Double = 1.0): RolledLoot {
        val entries = index.tables.loot(tag).orEmpty()
        val goldRange = index.tables.gold(tag) ?: listOf(0L, 0L)
        val multiplier = rarity.quantity * (1 + quantity / 100)
        val gold = dice.betweenLong(goldRange) * rules.goldGrowth.pow((rules.goldTaper ?: campaign.growthTaper).steps(level)) * rarity.quantity * (1 + goldBonus / 100) * goldShare
        val items = mutableMapOf<String, Long>()
        val equipment = mutableListOf<List<String>>()
        entries.forEach { entry ->
            val chance = entry.chance ?: return@forEach
            val share = if (entry.kind == TableKind.ITEM && index.item(entry.code)?.category == Item.CURRENCY) orbShare else 1.0
            repeat(dice.times(chance * multiplier * share)) {
                when {
                    Ref.isTable(entry.ref) -> equipment += listOf(entry.code)
                    entry.kind == TableKind.ITEM -> items.merge(entry.code, dice.betweenLong(entry.amount ?: listOf(1L, 1L)), Long::plus)
                    entry.kind == TableKind.TEMPLATE -> equipment += listOf(Ref.table(entry.code))
                }
            }
        }
        return RolledLoot(Math.round(gold), items, equipment)
    }

    /** Какой шаблон выпал из тяги: вес в таблице на вес редкости; всё, кроме обычного, растёт от [bonus] процентов редкости. */
    fun pick(candidates: List<Weighted<ItemTemplate>>, bonus: Double, dice: Dice): ItemTemplate? =
        Tables.draw(candidates, { (template, weight) ->
            val base = weight * (rules.rarityWeights[template.rarity] ?: 0.0)
            if (template.rarity == Rarity.COMMON) base else base * (1 + bonus / 100)
        }, dice)?.value

    /** Шаблон из таблиц [tags] на уровне [level] с бонусом редкости; прямой шаблон ([Ref.table] нет) - сам. */
    fun pickFrom(pools: List<String>, level: Int, bonus: Double, dice: Dice): ItemTemplate? {
        val tags = pools.map { if (Ref.isTable(it)) Ref.code(it) else it }
        return pick(index.templatePoolUpTo(tags, level), bonus, dice)
    }

    /** Уникалка из таблиц [tags] не старше `level + uniqueReach`; за неимением - любая из них. */
    fun unique(tags: List<String>, level: Int, dice: Dice): ItemTemplate? =
        Tables.draw(index.templatePoolUpTo(tags, level + rules.uniqueReach).ifEmpty { index.templatePool(tags) }, dice)

    /** Выпала ли карта: [chance] уже с количеством; карта следующей зоны - одна из [next] наугад. */
    fun mapDrop(chance: Double, mapCode: String, next: List<String>, dice: Dice, nextBonus: Double = 0.0): String? {
        if (!dice.chance(chance)) return null
        return if (next.isNotEmpty() && dice.chance(campaign.maps.nextChance * (1 + nextBonus / 100))) dice.pick(next) else mapCode
    }

    /** Редкость упавшей карты по таблице; [rareBonus] - проценты к весу редкой. */
    fun mapRarity(dice: Dice, rareBonus: Double = 0.0): Rarity =
        Tables.value<Rarity>(index.tables, campaign.maps.rarities, dice) { if (it == Rarity.RARE) 1 + rareBonus / 100 else 1.0 } ?: Rarity.COMMON

    /** Сколько процентов даёт риск карты: единица каждой вредной строки по её весу. */
    fun risk(effects: Map<String, Double>): Double = Math.round(effects.entries.sumOf { (stat, value) -> value * (campaign.maps.risk[stat] ?: 0.0) } * 10) / 10.0

    /** Карта в действии: риск и прямые строки - к количеству, редкости и опыту; редкость самой карты - к первым двум. */
    fun activeMap(mapCode: String, effects: Map<String, Double>, rarity: Rarity = Rarity.COMMON): ActiveMap {
        val risk = risk(effects)
        val own = campaign.maps.rarityBonus[rarity] ?: 0.0
        return ActiveMap(mapCode, effects, risk + own + (effects[MapStat.QUANTITY.code] ?: 0.0), risk + own + (effects[MapStat.RARITY.code] ?: 0.0), risk + (effects[MapStat.EXPERIENCE.code] ?: 0.0), rarity)
    }

    /** Строки карты-предмета, сложенные по характеристикам, с множителем атласа. */
    fun mapEffects(item: ItemInstance, atlasEffect: Double = 1.0): Map<String, Double> {
        val effects = mutableMapOf<String, Double>()
        item.rolls.forEach { roll ->
            val def = index.modifier(roll.code) ?: return@forEach
            val values = roll.values(def)
            def.effects.forEachIndexed { i, effect -> effects.merge(effect.stat, values.getOrElse(i) { 0.0 }, Double::plus) }
        }
        return effects.mapValues { (_, value) -> Math.round(value * atlasEffect * 10) / 10.0 }
    }

    /** Код шаблона карты локации. */
    fun mapTemplate(mapCode: String): String = "MAP_$mapCode"

    /** Что стоила смерть: доля опыта уровня, не ниже его порога. */
    fun deathLoss(rule: DeathRule, mapLevel: Int, experience: Double, floor: Double, next: Double?): Double {
        if (mapLevel < rule.fromLevel || rule.experienceShare <= 0 || next == null || next <= floor) return 0.0
        val penalty = (next - floor) * rule.experienceShare / 100
        return Math.round(penalty.coerceAtMost((experience - floor).coerceAtLeast(0.0))).toDouble()
    }
}
