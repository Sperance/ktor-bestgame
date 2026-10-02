package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.LootChest
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.run.Reward

/**
 * Сундуки-добыча (1.71.0): где они падают и что дают открытыми. Броски падения идут списком `lootChests` по порядку и
 * последними в своей награде - прежние потоки костей не сдвигаются.
 */
class LootChests(private val index: ContentIndex) {
    private val chests: List<LootChest> get() = index.campaign.lootChests

    /** Сундуки с монстра редкости [rarity] в зоне уровня [level]; [finale] - босс финала региона; [quantity] - проценты количества. */
    fun fromMonster(level: Int, rarity: MonsterRarity, finale: Boolean, quantity: Double, dice: Dice): Map<String, Long> =
        roll(level, dice) { chest ->
            if (rarity == MonsterRarity.UNIQUE) chest.bossChance + if (finale) chest.finaleChance else 0.0
            else chest.dropChance * index.campaign.rarity(rarity).quantity * (1 + quantity / 100)
        }

    /** Сундуки из награды испытания на уровне [level]. */
    fun fromTrial(level: Int, dice: Dice): Map<String, Long> = roll(level, dice) { it.trialChance }

    private fun roll(level: Int, dice: Dice, chance: (LootChest) -> Double): Map<String, Long> {
        val found = LinkedHashMap<String, Long>()
        chests.forEach { chest ->
            val p = chance(chest)
            if (p > 0 && level >= chest.minLevel && dice.chance(p.coerceAtMost(1.0))) found.merge(chest.code, 1L, Long::plus)
        }
        return found
    }

    /**
     * Открытый сундук [chest] у героя уровня [heroLevel]: золото, стопки и вещи его таблицы, уникалка с шансом и его
     * собственные стопки. Id вещей даёт [newId].
     */
    fun open(chest: LootChest, heroLevel: Int, dice: Dice, newId: () -> String): Reward {
        val loot = LootRoller(index, heroLevel)
        val factory = ItemFactory(index)
        val rule = index.campaign.rarity(chest.rarity)
        val itemLevel = index.rules.loot.itemLevel(heroLevel, chest.rarity)
        var gold = 0L
        val items = LinkedHashMap<String, Long>()
        val equipment = mutableListOf<ItemInstance>()
        repeat(chest.rolls.coerceAtLeast(1)) {
            val rolled = loot.roll(chest.table, heroLevel, rule, 0.0, 0.0, dice)
            gold += rolled.gold
            rolled.items.forEach { (code, amount) -> items.merge(code, amount, Long::plus) }
            rolled.equipment.mapNotNull { pools -> loot.pickFrom(pools, heroLevel, rule.rarityBonus, dice) }
                .forEach { template -> equipment += factory.create(newId(), template, template.rarity, dice, level = itemLevel) }
        }
        if (chest.uniqueTables.isNotEmpty() && dice.chance(chest.uniqueChance))
            loot.unique(chest.uniqueTables, heroLevel, dice)?.let { equipment += factory.create(newId(), it, Rarity.UNIQUE, dice, level = itemLevel) }
        chest.items.forEach { (code, range) ->
            val amount = if (range.isEmpty()) 0L else dice.betweenLong(range)
            if (amount > 0) items.merge(code, amount, Long::plus)
        }
        return Reward(0.0, gold, items, equipment)
    }
}
