package features.logic.campaign

import application.enums.EnumMonsterRarity
import application.enums.EnumRarity
import features.logic.pools.Weighted
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/** Что выпало из одного монстра, до того как оно легло персонажу; экипировка - пулами, из которых её тянуть. */
data class RolledLoot(val gold: Long, val orbs: Map<String, Long>, val equipment: List<List<String>>)

/**
 * Добыча и опыт за убитого монстра - правила сервера.
 *
 * Бой считает клиент, но что из монстра выпало, решает только сервер: клиент сообщает, кого
 * он убил и какой редкости тот был, а числа берутся отсюда. Функции чистые и принимают
 * [Random], чтобы тест мог проверить их без базы.
 */
object CampaignLoot {

    /** Как быстро растёт золото с уровнем карты. */
    private const val GOLD_GROWTH = 1.1

    /** Показатель степени опыта: на двадцатой карте монстр стоит в сотни раз больше, чем на первой. */
    private const val EXPERIENCE_POWER = 1.9

    /**
     * Вес шаблона экипировки по его редкости; всё, кроме обычного, растёт от бонуса редкости.
     * Мифический впятеро реже уникального (0.53.0), а с карт ниже 15-й не падает вовсе: его
     * требуемый уровень выше, чем отдаёт такой пул, см. EquipmentCache.poolUpTo.
     */
    private val rarityWeights = mapOf(
        EnumRarity.COMMON to 100.0, EnumRarity.UNCOMMON to 40.0, EnumRarity.RARE to 15.0,
        EnumRarity.UNIQUE to 1.0, EnumRarity.MYTHICAL to 0.2,
    )

    /**
     * Опыт за монстра: его база, уровень карты, редкость и бонус героя к опыту в процентах.
     */
    fun experience(monster: MonsterTemplate, level: Int, rarity: CampaignRarity, bonus: Double): Double =
        Math.round(monster.experience * level.toDouble().pow(EXPERIENCE_POWER) * rarity.experience * (1 + bonus / 100)).toDouble()

    /**
     * Броски по таблице добычи.
     *
     * Количество - это множитель шанса: у магического монстра каждая строка выпадает вдвое чаще,
     * а шанс больше единицы даёт целое число гарантированных выпадений и остаток шансом.
     *
     * @param quantity бонус героя к количеству предметов, в процентах
     * @param goldBonus бонус героя к золоту, в процентах
     */
    fun roll(table: LootTable, level: Int, rarity: CampaignRarity, quantity: Double, goldBonus: Double, random: Random): RolledLoot {
        val multiplier = rarity.quantity * (1 + quantity / 100)
        val gold = random.nextLong(table.gold[0], table.gold[1] + 1) * GOLD_GROWTH.pow(level - 1) * rarity.quantity * (1 + goldBonus / 100)
        val orbs = mutableMapOf<String, Long>()
        val equipment = mutableListOf<List<String>>()
        table.drops.forEach { drop ->
            repeat(times(drop.chance * multiplier, random)) {
                when (drop.kind) {
                    EnumLootKind.ORB -> orbs.merge(drop.code, random.nextLong(drop.amount[0], drop.amount[1] + 1), Long::plus)
                    EnumLootKind.EQUIPMENT -> equipment += drop.equipmentPools
                }
            }
        }
        return RolledLoot(Math.round(gold), orbs, equipment)
    }

    /**
     * Какой шаблон экипировки выпал из пула: вес в пуле, умноженный на вес редкости - чаще простой.
     *
     * @param bonus бонус редкости - монстра и героя вместе, в процентах
     */
    fun <T> pick(candidates: List<Weighted<T>>, rarity: (T) -> EnumRarity, bonus: Double, random: Random): T? {
        if (candidates.isEmpty()) return null
        val weights = candidates.map { (candidate, weight) ->
            val base = weight * (rarityWeights[rarity(candidate)] ?: 0.0)
            if (rarity(candidate) == EnumRarity.COMMON) base else base * (1 + bonus / 100)
        }
        var point = random.nextDouble() * weights.sum()
        candidates.forEachIndexed { index, candidate ->
            point -= weights[index]
            if (point <= 0) return candidate.value
        }
        return candidates.last().value
    }

    private fun times(expected: Double, random: Random): Int {
        val whole = floor(expected).toInt()
        return whole + if (random.nextDouble() < expected - whole) 1 else 0
    }
}

/**
 * Смерть героя - правило сервера, как и добыча (с 0.28.0).
 *
 * Как в PoE: с карты уровня `death.fromLevel` смерть отнимает `death.experienceShare` процентов
 * опыта текущего уровня - от его порога до следующего, - но никогда не опускает ниже порога,
 * так что уровень не падает. Функция чистая: тест проверяет её без базы.
 */
object CampaignDeath {

    /**
     * Сколько опыта теряется.
     *
     * @param experience опыт героя сейчас
     * @param floor порог текущего уровня
     * @param next порог следующего уровня; null - уровень последний, терять нечего
     */
    fun lost(rule: DeathRule, mapLevel: Int, experience: Double, floor: Double, next: Double?): Double {
        if (mapLevel < rule.fromLevel || rule.experienceShare <= 0 || next == null || next <= floor) return 0.0
        val penalty = (next - floor) * rule.experienceShare / 100
        return Math.round(penalty.coerceAtMost((experience - floor).coerceAtLeast(0.0))).toDouble()
    }
}

/**
 * Окно сундуков одной карты у одного героя (с 0.31.0): [left] ещё можно открыть до [refreshAt]
 * (миллисекунды эпохи), после чего окно бросается заново.
 */
@kotlinx.serialization.Serializable
data class ChestWindow(val refreshAt: Long = 0, val left: Int = 0, val bought: Boolean = false)

/**
 * Сундуки - правило сервера, как и добыча. Функции чистые: время и [Random] приходят снаружи.
 */
object CampaignChests {

    /**
     * Окно на момент [now]: живое остаётся как есть, истёкшее (или отсутствующее) бросается заново.
     *
     * @param bonus `STOCK_CHEST_QUANTITY` героя в процентах: каждые полные 100 - ещё один сундук, остаток - шанс
     * @param extra сундуки сверх броска (`ATLAS_CHESTS`, с 0.60.0); ложатся только в новое окно, живое не меняется
     */
    fun window(current: ChestWindow?, now: Long, rule: ChestRule, bonus: Double, random: Random, extra: Int = 0): ChestWindow {
        if (current != null && now < current.refreshAt) return current
        val base = random.nextInt(rule.count[0], rule.count[1] + 1)
        val share = bonus.coerceAtLeast(0.0) / 100
        val whole = floor(share).toInt()
        val count = (base + whole + extra + if (random.nextDouble() < share - whole) 1 else 0).coerceAtLeast(0)
        return ChestWindow(now + (rule.refreshHours * 3_600_000).toLong(), count)
    }

    /** Редкость, с которой катается добыча сундука: множитель количества и бонус редкости правила. */
    fun rarity(rule: ChestRule): CampaignRarity =
        CampaignRarity(EnumMonsterRarity.NORMAL, 0, listOf(0, 0), quantity = rule.quantity, rarityBonus = rule.rarityBonus)
}

/**
 * Карта, с которой герой вошёл в локацию (с 0.35.0): её модификаторы, сложенные по характеристикам,
 * и то, что они прибавляют к добыче этой локации, - в процентах к количеству, редкости и опыту.
 * [itemRarity] (с 0.60.0) - редкость самой карты: выход с редкой приносит очко атласа.
 */
@kotlinx.serialization.Serializable
data class ActiveMap(
    val mapCode: String,
    val effects: Map<String, Double> = emptyMap(),
    val quantity: Double = 0.0,
    val rarity: Double = 0.0,
    val experience: Double = 0.0,
    val itemRarity: EnumRarity = EnumRarity.COMMON,
)

/** Карты - правило сервера, как и добыча. Функции чистые: [Random] приходит снаружи. */
object CampaignMaps {

    const val QUANTITY = "MAP_QUANTITY"
    const val RARITY = "MAP_RARITY"
    const val EXPERIENCE = "MAP_EXPERIENCE"
    const val CHESTS = "MAP_CHESTS"
    const val GOLD = "MAP_GOLD"
    const val FOUNTAINS = "MAP_FOUNTAINS"
    const val BOSS_POWER = "MAP_BOSS_POWER"

    /** Код шаблона карты для локации: `MAP_<код локации>`. */
    fun templateCode(mapCode: String) = "MAP_$mapCode"

    /** Сколько процентов даёт риск карты: каждая единица вредного модификатора по его весу. */
    fun risk(rule: MapRule, effects: Map<String, Double>): Double =
        Math.round(effects.entries.sumOf { (stat, value) -> value * (rule.risk[stat] ?: 0.0) } * 10) / 10.0

    /**
     * Карта в действии: риск прибавляется к количеству, редкости и опыту вместе с их прямыми
     * модификаторами, а редкость самой карты (0.42.0) - к количеству и редкости.
     */
    fun active(rule: MapRule, mapCode: String, effects: Map<String, Double>, rarity: EnumRarity = EnumRarity.COMMON): ActiveMap {
        val risk = risk(rule, effects)
        val own = rule.rarityBonus[rarity] ?: 0.0
        return ActiveMap(mapCode, effects, risk + own + (effects[QUANTITY] ?: 0.0), risk + own + (effects[RARITY] ?: 0.0), risk + (effects[EXPERIENCE] ?: 0.0), rarity)
    }

    /**
     * Выпала ли карта и какой локации.
     *
     * @param chance шанс выпадения, уже умноженный на количество
     * @param next зоны, в которые ведут связи из [mapCode] (0.67.0): «карта следующей зоны» - одна из них наугад
     */
    fun drop(rule: MapRule, chance: Double, mapCode: String, next: List<String>, random: Random, nextBonus: Double = 0.0): String? {
        if (random.nextDouble() >= chance) return null
        return if (next.isNotEmpty() && random.nextDouble() < rule.nextChance * (1 + nextBonus / 100)) next.random(random) else mapCode
    }

    /** Редкость упавшей карты по весам правила; [rareBonus] (атлас, 0.66.0) - проценты к весу редкой. */
    fun rarity(rule: MapRule, random: Random, rareBonus: Double = 0.0): EnumRarity {
        val weights = rule.rarities.mapValues { (rarity, weight) -> if (rarity == EnumRarity.RARE) weight * (1 + rareBonus / 100) else weight.toDouble() }
        var point = random.nextDouble() * weights.values.sum()
        weights.forEach { (rarity, weight) ->
            point -= weight
            if (point < 0) return rarity
        }
        return rule.rarities.keys.first()
    }
}
