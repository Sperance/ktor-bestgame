package com.sperance.exileforge.rules.run

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Monster
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.RarityRule
import com.sperance.exileforge.rules.content.Zone
import com.sperance.exileforge.rules.roll.AbyssRifts
import com.sperance.exileforge.rules.roll.ActiveMap
import com.sperance.exileforge.rules.roll.Chests
import com.sperance.exileforge.rules.roll.Crystal
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.HoardBonus
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.LootRoller
import com.sperance.exileforge.rules.roll.MonsterMod
import com.sperance.exileforge.rules.roll.MonsterRoller
import com.sperance.exileforge.rules.roll.RolledMonster
import com.sperance.exileforge.rules.roll.Streams
import com.sperance.exileforge.rules.roll.VaalZone
import com.sperance.exileforge.rules.table.Tables
import kotlinx.serialization.Serializable

/** Проценты героя к добыче с монстров одной редкости: лист, карта, атлас и силы уникалок, уже сложенные сервером. */
@Serializable
data class RarityBonus(
    val quantity: Double = 0.0,
    val rarity: Double = 0.0,
    val experience: Double = 0.0,
    val gold: Double = 0.0,
    val map: Double = 0.0,
    val book: Double = 0.0,
    val unique: Double = 0.0,
)

/**
 * Контекст захода: всё, что сервер посчитал о герое на входе в зону и по чему обе стороны катают
 * добычу одинаково. Замораживается на заход, чтобы предпросмотр клиента совпал с начислением.
 */
@Serializable
data class RunContext(
    val heroClass: String,
    val heroLevel: Int,
    /** По имени редкости монстра: ключ строкой, чтобы документ героя и провод читали его одинаково. */
    val bonuses: Map<String, RarityBonus> = emptyMap(),
    val atlas: Map<String, Double> = emptyMap(),
    val active: ActiveMap? = null,
    val vaal: VaalZone? = null,
    /** Лишние строки редких монстров от атласа. */
    val extraRareMods: Int = 0,
    /** Куда ведут связи из зоны: карта следующей зоны - одна из них. */
    val next: List<String> = emptyList(),
    /** Известные рецепты верстака: новый рецепт с редкого монстра - только незнакомый. */
    val recipes: List<String> = emptyList(),
) {
    fun bonus(rarity: MonsterRarity): RarityBonus = bonuses[rarity.name] ?: RarityBonus()
    operator fun get(atlasStat: String): Double = atlas[atlasStat] ?: 0.0
}

@Serializable
enum class RunEventKind { KILL, CHEST, BOSS, CORRUPT, CRYSTAL, CRYSTAL_VAAL, VAAL_OPEN, VAAL_LEAVE, ABYSS_OPEN, ABYSS_CLAIM, SUMMON, FALL, LEAVE }

/**
 * Событие захода в журнале клиента: порядковый номер [n] (сервер применяет каждый номер один раз),
 * вид, жетон [i] и член пака [m] у убийства, [index] сундука, кристалла или расщелины, глубина Бездны.
 */
@Serializable
data class RunEvent(
    val n: Int,
    val kind: RunEventKind,
    val i: Int = 0,
    val m: Int = 0,
    val index: Int = 0,
    val depth: Int = 0,
    val fallen: Boolean = false,
    /** Убийство внутри Ваал-зоны: жетон из её ряда, добыча с её бонусом. */
    val vaal: Boolean = false,
)

/**
 * Заход, выданный сервером на вход в зону: по [seed] и [context] клиент катает монстров и добычу тем
 * же кодом, сервер проигрывает журнал событий тем же семенем. [count] - жетонов в зоне.
 */
@Serializable
data class RunStart(val id: String, val seed: Long, val zone: String, val level: Int, val context: RunContext, val count: Int, val startedAt: Long)

/** Что принесло одно событие: опыт, золото, стопки по коду предмета, копии вещей, найденный рецепт. */
data class Reward(
    val experience: Double = 0.0,
    val gold: Long = 0,
    val items: Map<String, Long> = emptyMap(),
    val equipment: List<ItemInstance> = emptyList(),
    val recipe: String? = null,
) {
    operator fun plus(other: Reward) = Reward(experience + other.experience, gold + other.gold,
        (items.keys + other.items.keys).associateWith { (items[it] ?: 0L) + (other.items[it] ?: 0L) }, equipment + other.equipment, recipe ?: other.recipe)

    companion object { val NONE = Reward() }
}

/** Жетон карты: пак монстров, стоящий на одном месте; первый - вожак. */
data class Spawn(val index: Int, val pack: List<RolledMonster>)

/**
 * Заход по семени: потоки костей на каждое событие независимы от порядка, поэтому клиент играет
 * заход как хочет, а сервер проигрывает журнал событие за событием тем же кодом.
 */
class Run(val index: ContentIndex, val zone: Zone, val seed: Long, val context: RunContext) {
    val streams = Streams(seed)
    val monsters = MonsterRoller(index)
    val loot = LootRoller(index)
    val factory = ItemFactory(index)
    val pool: List<MonsterMod> = monsters.zonePool(zone)
    private val campaign get() = index.campaign

    /** Сколько жетонов встаёт на карте: бросок зоны плюс строки карты `MAP_PACK`. */
    val count: Int by lazy {
        val dice = streams.of("count")
        (dice.between(zone.count) * (1 + (context.active?.effects?.get("MAP_PACK") ?: 0.0) / 100)).toInt().coerceAtLeast(1)
    }

    /** Жетонов в Ваал-зоне: бросок зоны на своём потоке. */
    val vaalCount: Int by lazy { streams.of("vaalCount").between(zone.count).coerceAtLeast(1) }

    /** Жетон [i]: вожак и до двух спутников с шансом [PACK_CHANCE]; редкость каждого - по таблице зоны и строкам карты. */
    fun spawn(i: Int, vaal: Boolean = false): Spawn {
        val dice = streams.of(if (vaal) "vaalMonster" else "monster", i)
        val packDice = streams.of(if (vaal) "vaalPack" else "pack", i)
        val size = if (packDice.chance(PACK_CHANCE)) 1 + packDice.nextInt(PACK_MAX) else 1
        val members = List(size) { m -> if (m == 0) roll(dice) else roll(packDice) }
        return Spawn(i, members)
    }

    private fun roll(dice: Dice): RolledMonster {
        val code = dice.pick(zone.monsters)
        val rule = rarityRule(dice)
        return monsters.roll(zone, code, pool, dice, context.extraRareMods, rule)
    }

    /** Редкость монстра: таблица зоны, строки карты `MAP_MAGIC`/`MAP_RARE` поднимают веса волшебных и редких. */
    private fun rarityRule(dice: Dice): RarityRule {
        val magic = 1 + (context.active?.effects?.get("MAP_MAGIC") ?: 0.0) / 100
        val rare = 1 + (context.active?.effects?.get("MAP_RARE") ?: 0.0) / 100
        val rarity = Tables.value<MonsterRarity>(index.tables, campaign.rarityTable, dice) { when (it) { MonsterRarity.MAGIC -> magic; MonsterRarity.RARE -> rare; else -> 1.0 } } ?: MonsterRarity.NORMAL
        return campaign.rarity(rarity)
    }

    /** Убит член [m] жетона [i]: добыча его таблицы, опыт, карта, книга и рецепт - на потоке события. */
    fun kill(i: Int, m: Int, vaal: Boolean = false): Reward? {
        if (vaal && context.vaal == null) return null
        if (i !in 0 until (if (vaal) vaalCount else count)) return null
        val monster = spawn(i, vaal).pack.getOrNull(m) ?: return null
        val template = index.monster(monster.code) ?: return null
        val rule = campaign.rarity(monster.rarity)
        val dice = streams.of("loot", i * PACK_SLOTS + m)
        val zoneBonus = if (vaal) context.vaal else null
        return grant(dice, template.loot, zone.level, rule, "k$i-$m", experienceFor(template, rule, zoneBonus),
            mapChance = campaign.maps.dropChance * rule.quantity, zoneBonus = zoneBonus, rare = monster.rarity == MonsterRarity.RARE,
            book = if (monster.rarity == MonsterRarity.RARE) index.skills.rules.books.rare else 0.0)
    }

    /** Сундук [k] открыт: таблица сундуков зоны с множителями правила и атласа. */
    fun chest(k: Int): Reward {
        val dice = streams.of("chest", k)
        val rule = Chests.rarity(campaign.chests)
        return grant(dice, zone.chestLoot, zone.level, rule, "c$k", 0.0, extraQuantity = context["ATLAS_CHEST_LOOT"])
    }

    /** Босс убит: его таблица уникальной редкостью, шанс мировой и собственной уникалки, книга своего класса чаще. */
    fun boss(): Reward {
        val dice = streams.of("boss")
        val template = index.monster(zone.boss)!!
        val rule = campaign.rarity(MonsterRarity.UNIQUE)
        val bosses = campaign.bosses
        val extra = listOfNotNull(
            loot.unique(bosses.tables, zone.level, dice).takeIf { dice.chance(uniqueChance(bosses.uniqueChance * relative("ATLAS_BOSS_UNIQUE"))) },
            Tables.draw(index.templatePool(template.tables), dice).takeIf { template.tables.isNotEmpty() && dice.chance(uniqueChance(bosses.ownUniqueChance * relative("ATLAS_BOSS_UNIQUE"))) },
        )
        val bossLoot = context["ATLAS_BOSS_LOOT"] + (context.active?.effects?.get("MAP_BOSS_POWER") ?: 0.0)
        return grant(dice, template.loot, zone.level, rule, "b", experienceFor(template, rule, null), extra, campaign.maps.bossChance, extraQuantity = bossLoot, rare = true,
            book = index.skills.rules.books.boss, ownShare = index.skills.rules.books.bossOwnClass)
    }

    /** Страж Ваал-зоны убит: своя таблица порчи с бонусом зоны и шанс уникалки порчи. */
    fun corrupt(): Reward? {
        val zoneBonus = context.vaal ?: return null
        val dice = streams.of("corrupt")
        val template = index.monster(zone.corrupted)!!
        val rule = campaign.rarity(MonsterRarity.UNIQUE)
        val extra = listOfNotNull(loot.unique(campaign.corruption.tables, zone.level, dice).takeIf { dice.chance(uniqueChance(campaign.corruption.uniqueChance * relative("ATLAS_VAAL_UNIQUE"))) })
        return grant(dice, template.loot, zone.level, rule, "v", experienceFor(template, rule, zoneBonus), extra, zoneBonus = zoneBonus)
    }

    /** Страж кристалла [crystal] пал: эссенции кристалла, добыча редкого монстра и с шансом книга. */
    fun crystal(k: Int, crystal: Crystal): Reward {
        val dice = streams.of("crystal", k)
        val template = index.monster(crystal.guardian)!!
        val rule = campaign.rarity(MonsterRarity.RARE)
        val essences = crystal.essences.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        return grant(dice, template.loot, zone.level, rule, "e$k", experienceFor(template, rule, null), extraItems = essences, book = index.essences.crystals.bookChance)
    }

    /** Копилка Бездны за [depth] ступеней: волшебные и редкие базы с влиянием Бездны, сферы, уникалка, опыт; [keep] - уцелевшая доля. */
    fun hoard(depth: Int, keep: Double): Reward {
        val rule = campaign.abyss ?: return Reward.NONE
        val dice = streams.of("hoard", depth)
        val rifts = AbyssRifts(index)
        val roll = rifts.roll(rule, depth, hoardBonus(rule), keep, dice)
        val bases = index.templatePoolUpTo(rule.tables, zone.level).filter { (template) -> template.rarity < Rarity.UNIQUE && template.slot.influenceable }
        val equipment = roll.items.mapIndexedNotNull { n, rarity -> Tables.draw(bases, dice)?.let { factory.createInfluenced("h$depth-$n", it, rarity, Influence.ABYSS, dice) } } +
            listOfNotNull(loot.unique(rule.uniques, zone.level, dice).takeIf { roll.unique }?.let { factory.create("h$depth-u", it, Rarity.UNIQUE, dice) })
        return Reward(roll.experience, 0, roll.orbs, equipment.map { it.copy(id = itemId(it.id)) })
    }

    /** Прибавки к копилке героя в зоне: строки карты, атлас, силы уникалок; опыт единицы - обычный монстр Бездны с бонусом героя. */
    fun hoardBonus(rule: com.sperance.exileforge.rules.content.AbyssRule): HoardBonus {
        fun sum(mapStat: String, atlasStat: String) = (context.active?.effects?.get(mapStat) ?: 0.0) + context[atlasStat]
        val normal = campaign.rarity(MonsterRarity.NORMAL)
        val bonus = context.bonus(MonsterRarity.NORMAL).experience + (context.active?.experience ?: 0.0) + context["ATLAS_EXPERIENCE"]
        return HoardBonus(
            items = 1 + sum("MAP_ABYSS_HOARD", "ATLAS_ABYSS_HOARD") / 100,
            rare = sum("MAP_ABYSS_RARE", "ATLAS_ABYSS_RARE"),
            orbs = 1 + sum("MAP_ABYSS_ORBS", "ATLAS_ABYSS_ORBS") / 100,
            unique = uniqueChance(1 + sum("MAP_ABYSS_UNIQUE", "ATLAS_ABYSS_UNIQUE") / 100),
            experience = rule.monsters.mapNotNull(index::monster).map { loot.experience(it, zone.level, normal, bonus) }.average(),
        )
    }

    /** Сфера Ваал на кристалле [k]: исход и кристалл после неё - на потоке кристалла. */
    fun crystalVaal(k: Int, crystal: Crystal): Pair<String, Crystal> = com.sperance.exileforge.rules.roll.EssenceCrystals(index).vaal(crystal, streams.of("crystalVaal", k))

    /** Ваал-зона этого захода - катится на своём потоке один раз. */
    fun vaalZone(): VaalZone = com.sperance.exileforge.rules.roll.VaalZones(index).roll(zone.code, zone.level, streams.of("vaal"), com.sperance.exileforge.rules.content.AtlasBonuses(context.atlas))

    private fun experienceFor(monster: Monster, rule: RarityRule, zoneBonus: VaalZone?): Double =
        loot.experience(monster, zone.level, rule, context.bonus(rule.rarity).experience + (context.active?.experience ?: 0.0) + (zoneBonus?.experience ?: 0.0) + context["ATLAS_EXPERIENCE"])

    private fun uniqueChance(chance: Double): Double = chance * (1 + context.bonus(MonsterRarity.UNIQUE).unique / 100)
    private fun relative(atlasStat: String) = (1 + context[atlasStat] / 100).coerceAtLeast(0.0)

    /**
     * Добыча по таблице: сферы в стопки, вещи из таблиц строк с бонусом редкости, карта с шансом, книга
     * умения и рецепт верстака. Копии получают детерминированные id `<семя>-<событие>-<номер>`.
     */
    private fun grant(
        dice: Dice, table: String, level: Int, rule: RarityRule, event: String, experience: Double,
        extra: List<ItemTemplate> = emptyList(), mapChance: Double = 0.0, zoneBonus: VaalZone? = null, extraQuantity: Double = 0.0,
        extraItems: Map<String, Long> = emptyMap(), rare: Boolean = false, book: Double = 0.0, ownShare: Double = 0.0,
    ): Reward {
        val bonus = context.bonus(rule.rarity)
        val active = context.active
        val quantity = bonus.quantity + (active?.quantity ?: 0.0) + (zoneBonus?.quantity ?: 0.0) + context["ATLAS_QUANTITY"] + extraQuantity
        val gold = bonus.gold + (active?.effects?.get("MAP_GOLD") ?: 0.0) + context["ATLAS_GOLD"]
        val rolled = loot.roll(table, level, rule, quantity, gold, dice)
        val rarityBonus = rule.rarityBonus + bonus.rarity + (active?.rarity ?: 0.0) + (zoneBonus?.rarity ?: 0.0) + context["ATLAS_RARITY"]
        val templates = extra + rolled.equipment.mapNotNull { pools -> loot.pickFrom(pools, level, rarityBonus, dice) }
        val equipment = templates.mapIndexed { n, template -> factory.create(itemId("$event-$n"), template, template.rarity, dice) }.toMutableList()
        loot.mapDrop(mapChance * relative("ATLAS_MAP_DROP") * (1 + quantity / 100) * (1 + bonus.map / 100), zone.code, context.next, dice, context["ATLAS_MAP_NEXT"])
            ?.let { code -> index.template(loot.mapTemplate(code)) }?.let { template ->
                val map = factory.create(itemId("$event-map"), template, loot.mapRarity(dice, context["ATLAS_MAP_RARE"]), dice)
                if (dice.percent(context["ATLAS_MAP_AFFIX"])) factory.affixes.rollExtraAffix(template, map.rarity, map.rolls, dice)?.let { map.rolls = map.rolls + it }
                equipment += map
            }
        val items = rolled.items.toMutableMap()
        extraItems.forEach { (code, amount) -> items.merge(code, amount, Long::plus) }
        if (book > 0) {
            val boost = 1 + ((active?.effects?.get("MAP_BOOKS") ?: 0.0) + context["ATLAS_BOOKS"] + bonus.book) / 100
            val share = (ownShare * (1 + context["ATLAS_BOOKS_OWN"] / 100)).coerceAtMost(1.0)
            index.skillRules.dropBook(context.heroClass, level, book * boost, share, dice)?.let { items.merge(it, 1L, Long::plus) }
        }
        val recipe = if (rare && dice.chance(index.rules.loot.recipeChance * relative("ATLAS_RECIPE"))) com.sperance.exileforge.rules.roll.Bench(index).draw(context.recipes, level, dice)?.code else null
        return Reward(experience, rolled.gold, items, equipment, recipe)
    }

    /** Id копии этого захода: семя и событие, одинаковые на клиенте и сервере. */
    fun itemId(event: String): String = "r${java.lang.Long.toHexString(seed)}-$event"

    companion object {
        const val PACK_CHANCE = 0.15
        const val PACK_MAX = 3
        const val PACK_SLOTS = 8
    }
}
