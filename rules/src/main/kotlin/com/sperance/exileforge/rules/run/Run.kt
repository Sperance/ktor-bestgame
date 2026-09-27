package com.sperance.exileforge.rules.run

import com.sperance.exileforge.rules.content.AtlasStat
import com.sperance.exileforge.rules.content.MapStat
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
 * же кодом, сервер проигрывает журнал событий тем же семенем. [count] - жетонов в зоне. Вход заново в
 * открытый заход (1.1.0) возвращает его же: [applied] - с какого номера журнал продолжается, [killed] и
 * [vaalKilled] - уже убитые жетоны `i*8+m`, [tally] - сколько наград каждого вида уже выдано.
 */
@Serializable
data class RunStart(
    val id: String, val seed: Long, val zone: String, val level: Int, val context: RunContext, val count: Int, val startedAt: Long,
    val applied: Int = 0, val killed: List<Int> = emptyList(), val vaalKilled: List<Int> = emptyList(), val tally: RunTally = RunTally(),
)

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

/**
 * Сколько наград каждого вида заход уже выдал (1.1.0). Поток костей сундука, босса, стража, кристалла и
 * копилки берёт порядковый номер своей награды, а не то, что назвал клиент: повтор события или другой
 * номер сундука дают следующую награду, а не ту же самую второй раз. Сервер хранит счёт в заходе,
 * клиент ведёт свой в [Run] - на одних и тех же принятых событиях они совпадают.
 */
@Serializable
data class RunTally(var chests: Int = 0, var bosses: Int = 0, var corrupts: Int = 0, var crystals: Int = 0, var hoards: Int = 0)

/** Жетон карты: пак монстров, стоящий на одном месте; первый - вожак. */
data class Spawn(val index: Int, val pack: List<RolledMonster>)

/**
 * Заход по семени: потоки костей на каждое событие независимы от порядка, поэтому клиент играет
 * заход как хочет, а сервер проигрывает журнал событие за событием тем же кодом.
 */
class Run(val index: ContentIndex, val zone: Zone, val seed: Long, val context: RunContext, val tally: RunTally = RunTally()) {
    val streams = Streams(seed)
    val monsters = MonsterRoller(index)
    val loot = LootRoller(index)
    val factory = ItemFactory(index)
    val pool: List<MonsterMod> = monsters.zonePool(zone)
    private val campaign get() = index.campaign

    /** Строка карты захода по имени; ноль без карты. */
    private fun mapEffect(stat: String): Double = context.active?.effects?.get(stat) ?: 0.0

    /** Сколько жетонов встаёт на карте: бросок зоны, строки карты `MAP_PACK_SIZE` и атлас `ATLAS_PACK_SIZE`. */
    val count: Int by lazy {
        val dice = streams.of("count")
        (dice.between(zone.count) * (1 + (mapEffect(MapStat.PACK_SIZE.code) + context[AtlasStat.PACK_SIZE.code]) / 100)).toInt().coerceAtLeast(1)
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

    /**
     * Редкость монстра: таблица зоны; строки карты поднимают веса - `MAP_MONSTER_RARITY` обеих, `MAP_MAGIC_MONSTERS`
     * волшебных, `MAP_RARE_MONSTERS` и атлас `ATLAS_RARE_MONSTERS` редких, `MAP_MONSTER_MAGIC_MIN` убирает обычных.
     */
    private fun rarityRule(dice: Dice): RarityRule {
        val rarer = 1 + mapEffect(MapStat.MONSTER_RARITY.code) / 100
        val magic = rarer * (1 + mapEffect(MapStat.MAGIC_MONSTERS.code) / 100)
        val rare = rarer * (1 + (mapEffect(MapStat.RARE_MONSTERS.code) + context[AtlasStat.RARE_MONSTERS.code]) / 100)
        val magicFloor = mapEffect(MapStat.MONSTER_MAGIC_MIN.code) > 0
        val rarity = Tables.value<MonsterRarity>(index.tables, campaign.rarityTable, dice) {
            when (it) { MonsterRarity.NORMAL -> if (magicFloor) 0.0 else 1.0; MonsterRarity.MAGIC -> magic; MonsterRarity.RARE -> rare; else -> 1.0 }
        } ?: MonsterRarity.NORMAL
        return campaign.rarity(rarity)
    }

    /** Убит член [m] жетона [i]: добыча его таблицы, опыт, карта, книга и рецепт - на потоке события. */
    fun kill(i: Int, m: Int, vaal: Boolean = false): Reward? {
        if (vaal && context.vaal == null) return null
        if (i !in 0 until (if (vaal) vaalCount else count)) return null
        val monster = spawn(i, vaal).pack.getOrNull(m) ?: return null
        val template = index.monster(monster.code) ?: return null
        val rule = campaign.rarity(monster.rarity)
        val dice = streams.of(if (vaal) "vaalLoot" else "loot", i * PACK_SLOTS + m)
        val zoneBonus = if (vaal) context.vaal else null
        return grant(dice, template.loot, zone.level, rule, if (vaal) "kv$i-$m" else "k$i-$m", experienceFor(template, rule, zoneBonus),
            mapChance = campaign.maps.dropChance * rule.quantity, zoneBonus = zoneBonus, rare = monster.rarity == MonsterRarity.RARE,
            book = if (monster.rarity == MonsterRarity.RARE) index.skills.rules.books.rare else 0.0)
    }

    /** Открыт очередной сундук захода: таблица сундуков зоны с множителями правила и атласа. */
    fun chest(): Reward {
        val k = tally.chests++
        val dice = streams.of("chest", k)
        val rule = Chests.rarity(campaign.chests)
        return grant(dice, zone.chestLoot, zone.level, rule, "c$k", 0.0, extraQuantity = context[AtlasStat.CHEST_LOOT.code])
    }

    /** Босс убит: его таблица уникальной редкостью, шанс мировой и собственной уникалки, книга своего класса чаще. */
    fun boss(): Reward {
        val n = tally.bosses++
        val dice = streams.of("boss", n)
        val template = index.monster(zone.boss)!!
        val rule = campaign.rarity(MonsterRarity.UNIQUE)
        val bosses = campaign.bosses
        val extra = listOfNotNull(
            loot.unique(bosses.tables, zone.level, dice).takeIf { dice.chance(uniqueChance(bosses.uniqueChance * relative(AtlasStat.BOSS_UNIQUE.code))) },
            Tables.draw(index.templatePool(template.tables), dice).takeIf { template.tables.isNotEmpty() && dice.chance(uniqueChance(bosses.ownUniqueChance * relative(AtlasStat.BOSS_UNIQUE.code))) },
        )
        val bossLoot = context[AtlasStat.BOSS_LOOT.code] + (context.active?.effects?.get(MapStat.BOSS_POWER.code) ?: 0.0)
        return grant(dice, template.loot, zone.level, rule, "b$n", experienceFor(template, rule, null), extra, campaign.maps.bossChance, extraQuantity = bossLoot, rare = true,
            book = index.skills.rules.books.boss, ownShare = index.skills.rules.books.bossOwnClass, goldShare = bosses.goldShare, orbShare = bosses.orbShare)
    }

    /** Страж Ваал-зоны убит: своя таблица порчи с бонусом зоны и шанс уникалки порчи. */
    fun corrupt(): Reward? {
        val zoneBonus = context.vaal ?: return null
        val n = tally.corrupts++
        val dice = streams.of("corrupt", n)
        val template = index.monster(zone.corrupted)!!
        val rule = campaign.rarity(MonsterRarity.UNIQUE)
        val extra = listOfNotNull(loot.unique(campaign.corruption.tables, zone.level, dice).takeIf { dice.chance(uniqueChance(campaign.corruption.uniqueChance * relative(AtlasStat.VAAL_UNIQUE.code))) })
        return grant(dice, template.loot, zone.level, rule, "v$n", experienceFor(template, rule, zoneBonus), extra, zoneBonus = zoneBonus)
    }

    /** Страж кристалла [crystal] пал: эссенции кристалла, добыча редкого монстра и с шансом книга. */
    fun crystal(crystal: Crystal): Reward {
        val k = tally.crystals++
        val dice = streams.of("crystal", k)
        val template = index.monster(crystal.guardian)!!
        val rule = campaign.rarity(MonsterRarity.RARE)
        val essences = crystal.essences.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        return grant(dice, template.loot, zone.level, rule, "e$k", experienceFor(template, rule, null), extraItems = essences, book = index.essences.crystals.bookChance)
    }

    /** Копилка Бездны за [depth] ступеней: волшебные и редкие базы с влиянием Бездны, сферы, уникалка, опыт; [keep] - уцелевшая доля. */
    fun hoard(depth: Int, keep: Double): Reward {
        val rule = campaign.abyss ?: return Reward.NONE
        val n = tally.hoards++
        val dice = streams.of("hoard", n * HOARD_DEPTHS + depth)
        val rifts = AbyssRifts(index)
        val roll = rifts.roll(rule, depth, hoardBonus(rule), keep, dice)
        val bases = index.templatePoolUpTo(rule.tables, zone.level).filter { (template) -> template.rarity < Rarity.UNIQUE && template.slot.influenceable }
        val equipment = roll.items.mapIndexedNotNull { i, rarity -> Tables.draw(bases, dice)?.let { factory.createInfluenced("h$n-$depth-$i", it, rarity, Influence.ABYSS, dice) } } +
            listOfNotNull(loot.unique(rule.uniques, zone.level, dice).takeIf { roll.unique }?.let { factory.create("h$n-$depth-u", it, Rarity.UNIQUE, dice) })
        return Reward(roll.experience, 0, roll.orbs, equipment.map { it.copy(id = itemId(it.id)) })
    }

    /** Прибавки к копилке героя в зоне: строки карты, атлас, силы уникалок; опыт единицы - обычный монстр Бездны с бонусом героя. */
    fun hoardBonus(rule: com.sperance.exileforge.rules.content.AbyssRule): HoardBonus {
        fun sum(mapStat: String, atlasStat: String) = (context.active?.effects?.get(mapStat) ?: 0.0) + context[atlasStat]
        val normal = campaign.rarity(MonsterRarity.NORMAL)
        val bonus = context.bonus(MonsterRarity.NORMAL).experience + (context.active?.experience ?: 0.0) + context[AtlasStat.EXPERIENCE.code]
        return HoardBonus(
            items = 1 + sum(MapStat.ABYSS_HOARD.code, AtlasStat.ABYSS_HOARD.code) / 100,
            rare = sum(MapStat.ABYSS_RARE.code, AtlasStat.ABYSS_RARE.code),
            orbs = 1 + sum(MapStat.ABYSS_ORBS.code, AtlasStat.ABYSS_ORBS.code) / 100,
            unique = uniqueChance(1 + sum(MapStat.ABYSS_UNIQUE.code, AtlasStat.ABYSS_UNIQUE.code) / 100),
            experience = rule.monsters.mapNotNull(index::monster).map { loot.experience(it, zone.level, normal, bonus) }.average(),
        )
    }

    /** Сфера Ваал на кристалле [k]: исход и кристалл после неё - на потоке кристалла. */
    fun crystalVaal(k: Int, crystal: Crystal): Pair<String, Crystal> = com.sperance.exileforge.rules.roll.EssenceCrystals(index).vaal(crystal, streams.of("crystalVaal", k))

    /** Ваал-зона этого захода - катится на своём потоке один раз. */
    fun vaalZone(): VaalZone = com.sperance.exileforge.rules.roll.VaalZones(index).roll(zone.code, zone.level, streams.of("vaal"), com.sperance.exileforge.rules.content.AtlasBonuses(context.atlas))

    private fun experienceFor(monster: Monster, rule: RarityRule, zoneBonus: VaalZone?): Double =
        loot.experience(monster, zone.level, rule, context.bonus(rule.rarity).experience + (context.active?.experience ?: 0.0) + (zoneBonus?.experience ?: 0.0) + context[AtlasStat.EXPERIENCE.code])

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
        goldShare: Double = 1.0, orbShare: Double = 1.0,
    ): Reward {
        val bonus = context.bonus(rule.rarity)
        val active = context.active
        val quantity = bonus.quantity + (active?.quantity ?: 0.0) + (zoneBonus?.quantity ?: 0.0) + context[AtlasStat.QUANTITY.code] + extraQuantity
        val gold = bonus.gold + (active?.effects?.get(MapStat.GOLD.code) ?: 0.0) + context[AtlasStat.GOLD.code]
        val rolled = loot.roll(table, level, rule, quantity, gold, dice, goldShare, orbShare)
        val rarityBonus = rule.rarityBonus + bonus.rarity + (active?.rarity ?: 0.0) + (zoneBonus?.rarity ?: 0.0) + context[AtlasStat.RARITY.code]
        val templates = extra + rolled.equipment.mapNotNull { pools -> loot.pickFrom(pools, level, rarityBonus, dice) }
        val equipment = templates.mapIndexed { n, template -> factory.create(itemId("$event-$n"), template, template.rarity, dice) }.toMutableList()
        loot.mapDrop(mapChance * relative(AtlasStat.MAP_DROP.code) * (1 + quantity / 100) * (1 + bonus.map / 100), zone.code, context.next, dice, context[AtlasStat.MAP_NEXT.code])
            ?.let { code -> index.template(loot.mapTemplate(code)) }?.let { template ->
                val map = factory.create(itemId("$event-map"), template, loot.mapRarity(dice, context[AtlasStat.MAP_RARE.code]), dice)
                if (dice.percent(context[AtlasStat.MAP_AFFIX.code])) factory.affixes.rollExtraAffix(template, map.rarity, map.rolls, dice)?.let { map.rolls = map.rolls + it }
                equipment += map
            }
        val items = rolled.items.toMutableMap()
        extraItems.forEach { (code, amount) -> items.merge(code, amount, Long::plus) }
        if (book > 0) {
            val boost = 1 + ((active?.effects?.get(MapStat.BOOKS.code) ?: 0.0) + context[AtlasStat.BOOKS.code] + bonus.book) / 100
            val share = (ownShare * (1 + context[AtlasStat.BOOKS_OWN.code] / 100)).coerceAtMost(1.0)
            index.skillRules.dropBook(context.heroClass, level, book * boost, share, dice)?.let { items.merge(it, 1L, Long::plus) }
        }
        val recipe = if (rare && dice.chance(index.rules.loot.recipeChance * relative(AtlasStat.RECIPE.code))) com.sperance.exileforge.rules.roll.Bench(index).draw(context.recipes, level, dice)?.code else null
        return Reward(experience, rolled.gold, items, equipment, recipe)
    }

    /** Id копии этого захода: семя и событие, одинаковые на клиенте и сервере. */
    fun itemId(event: String): String = "r${java.lang.Long.toHexString(seed)}-$event"

    companion object {
        const val PACK_CHANCE = 0.20
        const val PACK_MAX = 3
        const val PACK_SLOTS = 8
        /** Глубин на одну копилку в потоке: номер копилки и глубина не пересекаются. */
        const val HOARD_DEPTHS = 1000
    }
}
