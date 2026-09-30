package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.AtlasStat
import com.sperance.exileforge.rules.content.MapStat
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
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
    /** Тир карты (1.41.0): его строки уже в [effects]; от него - тир упавшей карты и уникалки тиров. */
    val tier: Int = 0,
    /** Влияние захваченной карты (1.50.0): его сила уже в [effects]. */
    val influence: Influence? = null,
)

/** Добыча, опыт, карты и смерть - правила без состояния над [ContentIndex] и [Dice]. */
class LootRoller(private val index: ContentIndex) {
    private val rules get() = index.rules.loot
    private val campaign get() = index.campaign
    private val topZoneLevel: Int get() = campaign.zones.maxOfOrNull { it.level } ?: Int.MAX_VALUE

    /**
     * Опыт за монстра: база, уровень зоны в степени, редкость и бонус героя в процентах; с [heroLevel] - и
     * штраф за разницу уровней героя и зоны.
     */
    fun experience(monster: Monster, level: Int, rarity: RarityRule, bonus: Double, heroLevel: Int? = null): Double {
        // Выше самой высокой зоны герою некуда идти: разница считается от её уровня, а не от его
        val window = heroLevel?.let { rules.experienceWindow.share(minOf(it, topZoneLevel), level) } ?: 1.0
        return Math.round(monster.experience * level.toDouble().pow(rules.experiencePower) * rarity.experience * (1 + bonus / 100) * window).toDouble()
    }

    /**
     * Броски по таблице добычи [tag]: количество - множитель шанса каждой строки, шанс больше единицы -
     * гарантированные выпадения и остаток шансом; золото растёт со своим спадом или спадом роста монстров.
     */
    fun roll(tag: String, level: Int, rarity: RarityRule, quantity: Double, goldBonus: Double, dice: Dice, goldShare: Double = 1.0, orbShare: Double = 1.0,
             boosts: Map<String, Double> = emptyMap()): RolledLoot {
        val entries = index.tables.loot(tag).orEmpty()
        val goldRange = index.tables.gold(tag) ?: listOf(0L, 0L)
        val multiplier = rarity.quantity * (1 + quantity / 100)
        val gold = dice.betweenLong(goldRange) * rules.goldScale(level, campaign.growthTaper) * rarity.quantity * (1 + goldBonus / 100) * goldShare
        val items = mutableMapOf<String, Long>()
        val equipment = mutableListOf<List<String>>()
        entries.forEach { entry ->
            val chance = entry.chance ?: return@forEach
            val boost = if (entry.kind == TableKind.ITEM) boosts.entries.filter { entry.code.startsWith(it.key) }.sumOf { it.value } else 0.0
            val share = (if (entry.kind == TableKind.ITEM && index.item(entry.code)?.category == Item.CURRENCY) orbShare else 1.0) * (1 + boost / 100).coerceAtLeast(0.0)
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

    /**
     * Сколько процентов даёт риск карты: единица каждой вредной строки по её весу; бафы героя (1.14.0) весят
     * меньше нуля и срезают награду, но не ниже нуля.
     */
    fun risk(effects: Map<String, Double>): Double =
        Math.round(effects.entries.sumOf { (stat, value) -> value * (campaign.maps.risk[stat] ?: 0.0) }.coerceAtLeast(0.0) * 10) / 10.0

    /** Карта в действии: риск и прямые строки - к количеству, редкости и опыту; редкость самой карты - к первым двум. */
    fun activeMap(mapCode: String, base: Map<String, Double>, rarity: Rarity = Rarity.COMMON, tier: Int = 0, influence: Influence? = null, atlas: Map<String, Double> = emptyMap()): ActiveMap {
        val effects = if (influence == null) base else influenced(base, atlas)
        val risk = risk(effects)
        val own = campaign.maps.rarityBonus[rarity] ?: 0.0
        return ActiveMap(mapCode, effects, risk + own + (effects[MapStat.QUANTITY.code] ?: 0.0), risk + own + (effects[MapStat.RARITY.code] ?: 0.0),
            risk + (effects[MapStat.EXPERIENCE.code] ?: 0.0), rarity, tier, influence)
    }

    /** Сила захваченной карты (1.50.0): здоровье и урон монстров - правилом и узлами атласа; риск карты платит за них, как за её строки. */
    private fun influenced(effects: Map<String, Double>, atlas: Map<String, Double>): Map<String, Double> {
        val power = campaign.maps.influence.power + (atlas[AtlasStat.INFLUENCE_POWER.code] ?: 0.0)
        if (power <= 0) return effects
        return effects + listOf(MapStat.MONSTER_LIFE.code, MapStat.MONSTER_DAMAGE.code).associateWith { (effects[it] ?: 0.0) + power }
    }

    /** Захвачена ли упавшая карта и чем (1.50.0): шанс правила и узлов атласа, влияние - из пула атласа. */
    fun mapInfluence(dice: Dice, atlas: Map<String, Double>): Influence? {
        val rule = campaign.maps.influence
        if (!dice.percent(rule.chance + (atlas[AtlasStat.MAP_INFLUENCE.code] ?: 0.0))) return null
        return dice.pickOrNull(rule.pool(atlas))
    }

    /**
     * Тир упавшей карты уровня [mapLevel] (1.41.0): ниже порога тиров - 0; иначе тир карты захода (не меньше 1) и с шансом
     * `climb` - на ступень выше, до потолка.
     */
    fun mapTier(mapLevel: Int, activeTier: Int, dice: Dice, climbBonus: Double = 0.0): Int {
        val rule = campaign.maps.tiers ?: return 0
        if (mapLevel < rule.fromLevel) return 0
        val base = activeTier.coerceAtLeast(1)
        return (if (dice.percent(rule.climb * (1 + climbBonus / 100))) base + 1 else base).coerceAtMost(rule.max)
    }

    /**
     * Силы монстров атласа (1.41.0) - строками карты: здоровье, урон и скорость монстров карты-предмета, риск которых
     * сам даёт награду. [atlas] - сложенные строки атласа героя.
     */
    fun atlasPowers(effects: Map<String, Double>, atlas: Map<String, Double>): Map<String, Double> {
        val powers = mapOf(AtlasStat.MONSTER_LIFE to MapStat.MONSTER_LIFE, AtlasStat.MONSTER_DAMAGE to MapStat.MONSTER_DAMAGE, AtlasStat.MONSTER_SPEED to MapStat.MONSTER_SPEED)
        val out = effects.toMutableMap()
        powers.forEach { (from, to) -> atlas[from.code]?.takeIf { it != 0.0 }?.let { out.merge(to.code, it, Double::plus) } }
        return out
    }

    /** Строки карты-предмета, сложенные по характеристикам, с множителем атласа. */
    fun mapEffects(item: ItemInstance, atlasEffect: Double = 1.0): Map<String, Double> {
        val effects = mutableMapOf<String, Double>()
        item.rolls.forEach { roll ->
            val def = index.modifier(roll.code) ?: return@forEach
            val values = roll.values(def)
            def.effects.forEachIndexed { i, effect -> effects.merge(effect.stat, values.getOrElse(i) { 0.0 }, Double::plus) }
        }
        // Тир карты (1.41.0): его строки за каждую ступень - поверх строк самой карты.
        campaign.maps.tiers?.takeIf { item.mapTier > 0 }?.effects?.forEach { (stat, perTier) -> effects.merge(stat, perTier * item.mapTier, Double::plus) }
        return effects.mapValues { (_, value) -> Math.round(value * atlasEffect * 10) / 10.0 }
    }

    /** Что стоила смерть: доля опыта уровня, не ниже его порога. */
    fun deathLoss(rule: DeathRule, mapLevel: Int, experience: Double, floor: Double, next: Double?): Double {
        if (mapLevel < rule.fromLevel || rule.experienceShare <= 0 || next == null || next <= floor) return 0.0
        val penalty = (next - floor) * rule.experienceShare / 100
        return Math.round(penalty.coerceAtMost((experience - floor).coerceAtLeast(0.0))).toDouble()
    }
}
