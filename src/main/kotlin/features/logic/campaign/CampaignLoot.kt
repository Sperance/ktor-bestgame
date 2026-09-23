package features.logic.campaign

import application.enums.EnumRarity
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

/** Что выпало из одного монстра, до того как оно легло персонажу. */
data class RolledLoot(val gold: Long, val orbs: Map<String, Long>, val equipment: Int)

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

    /** Вес шаблона экипировки по его редкости; всё, кроме обычного, растёт от бонуса редкости. */
    private val rarityWeights = mapOf(
        EnumRarity.COMMON to 100.0, EnumRarity.UNCOMMON to 40.0, EnumRarity.RARE to 15.0,
        EnumRarity.EPIC to 5.0, EnumRarity.MYTHICAL to 1.5, EnumRarity.UNIQUE to 1.0,
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
        var equipment = 0
        table.drops.forEach { drop ->
            repeat(times(drop.chance * multiplier, random)) {
                when (drop.kind) {
                    EnumLootKind.ORB -> orbs.merge(drop.code, random.nextLong(drop.amount[0], drop.amount[1] + 1), Long::plus)
                    EnumLootKind.EQUIPMENT -> equipment++
                }
            }
        }
        return RolledLoot(Math.round(gold), orbs, equipment)
    }

    /**
     * Какой шаблон экипировки выпал: любой, что надевается на уровне карты, чаще простой.
     *
     * @param bonus бонус редкости - монстра и героя вместе, в процентах
     */
    fun <T> pick(candidates: List<T>, rarity: (T) -> EnumRarity, bonus: Double, random: Random): T? {
        if (candidates.isEmpty()) return null
        val weights = candidates.map { candidate ->
            val base = rarityWeights[rarity(candidate)] ?: 0.0
            if (rarity(candidate) == EnumRarity.COMMON) base else base * (1 + bonus / 100)
        }
        var point = random.nextDouble() * weights.sum()
        candidates.forEachIndexed { index, candidate ->
            point -= weights[index]
            if (point <= 0) return candidate
        }
        return candidates.last()
    }

    private fun times(expected: Double, random: Random): Int {
        val whole = floor(expected).toInt()
        return whole + if (random.nextDouble() < expected - whole) 1 else 0
    }
}
