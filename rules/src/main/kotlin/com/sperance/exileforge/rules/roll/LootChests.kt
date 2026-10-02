package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ChestGear
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.LootChest
import com.sperance.exileforge.rules.content.MAP_TEMPLATE
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TemplateKind
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted

/**
 * Сундуки-добыча (1.71.0, тиры с 1.72.0): где они падают и что дают открытыми. Броски падения идут списком `lootChests`
 * по порядку и последними в своей награде - прежние потоки костей не сдвигаются. Падает тир сундука по уровню источника.
 */
class LootChests(private val index: ContentIndex) {
    private val chests: List<LootChest> get() = index.campaign.lootChests

    /** Сундуки с монстра редкости [rarity] в зоне уровня [level]; [finale] - босс финала региона; [quantity] - проценты количества. */
    fun fromMonster(level: Int, rarity: MonsterRarity, finale: Boolean, quantity: Double, dice: Dice): Map<String, Long> =
        roll(level, dice) { chest ->
            if (rarity == MonsterRarity.UNIQUE) chest.bossChance + if (finale) chest.finaleChance else 0.0
            else chest.dropChance * index.campaign.rarity(rarity).quantity * (1 + quantity / 100)
        }

    /** Сундуки из награды испытания на уровне арены [level]: шанс растёт с её уровнем. */
    fun fromTrial(level: Int, dice: Dice): Map<String, Long> =
        roll(level, dice) { if (it.trialChance <= 0) 0.0 else (it.trialChance + it.trialPerLevel * level).coerceAtMost(it.trialMax) }

    private fun roll(level: Int, dice: Dice, chance: (LootChest) -> Double): Map<String, Long> {
        val found = LinkedHashMap<String, Long>()
        chests.forEach { chest ->
            val p = chance(chest)
            if (p > 0 && level >= chest.minLevel && dice.chance(p.coerceAtMost(1.0))) found.merge(chest.tierCode(LootChest.tierOf(level)), 1L, Long::plus)
        }
        return found
    }

    /** Сундук [base] тира уровня [level], если такой бывает: награда задания. */
    fun code(base: String, level: Int): String? = chests.firstOrNull { it.code == base }?.let { chest ->
        LootChest.tierOf(level.coerceAtLeast(chest.minLevel)).let { tier -> chest.tierCode(tier).takeIf { tier in chest.tiers } }
    }

    /** Вид и тир сундука по коду предмета, или ничего - это не сундук. */
    fun resolve(code: String): Pair<LootChest, Int>? {
        val tier = code.substringAfterLast("_T", "").toIntOrNull() ?: return null
        val chest = chests.firstOrNull { it.code == code.substringBeforeLast("_T") } ?: return null
        return (chest to tier).takeIf { tier in chest.tiers }
    }

    /**
     * Открытый сундук [chest] тира [tier] у героя класса [heroClass]: всё катится на верхнем уровне тира. Id вещей даёт [newId].
     */
    fun open(chest: LootChest, tier: Int, heroClass: String, dice: Dice, newId: () -> String): Reward {
        val level = LootChest.levelOf(tier)
        val loot = LootRoller(index, level)
        val factory = ItemFactory(index)
        val itemLevel = index.rules.loot.itemLevel(level, chest.rarity)
        val main = heroClassMain(index.classes.classes.firstOrNull { it.code == heroClass })
        var gold = 0L
        val items = LinkedHashMap<String, Long>()
        val equipment = mutableListOf<ItemInstance>()
        chest.tableOf(tier)?.let { table ->
            // Шансы и золото таблицы - как записаны: множитель количества редкости к ним не прибавляется.
            val rule = index.campaign.rarity(chest.rarity).copy(quantity = 1.0)
            repeat(chest.rolls.coerceAtLeast(1)) {
                val rolled = loot.roll(table, level, rule, 0.0, 0.0, dice)
                gold += rolled.gold
                rolled.items.forEach { (code, amount) -> items.merge(code, amount, Long::plus) }
            }
            if (chest.jackpotChance > 0 && dice.chance(chest.jackpotChance)) gold = Math.round(gold * chest.jackpot)
        }
        chest.gear.forEach { spec ->
            repeat(spec.count) {
                if (!dice.chance(spec.chance)) return@repeat
                gear(loot, spec, chest, level, main, dice)?.let { template ->
                    val rarity = if (spec.rarity == Rarity.MAGIC && dice.chance(spec.rareChance)) Rarity.RARE else spec.rarity
                    val item = if (spec.influenced && template.slot.influenceable)
                        factory.createInfluenced(newId(), template, rarity, dice.pick(Influence.entries), dice, itemLevel)
                    else factory.create(newId(), template, rarity, dice, level = itemLevel)
                    // Осквернённая сферой Ваал, как её осквернил бы игрок: что вышло, то и легло; вещь, что сферы не берёт, - как есть.
                    equipment += if (!spec.corrupted) item
                        else runCatching { OrbApplier(index).apply(Orb.VAAL_ORB, item, template, dice, null, newId).item }.getOrDefault(item)
                }
            }
        }
        if (chest.uniqueTables.isNotEmpty() && dice.chance(chest.uniqueChance)) unique(chest, level, heroClass, dice)?.let {
            equipment += factory.create(newId(), it, Rarity.UNIQUE, dice, level = itemLevel)
        }
        chest.maps?.let { rule ->
            val template = index.template(MAP_TEMPLATE)
            val zones = index.campaign.zones.filter { LootChest.tierOf(it.level) in (tier - rule.spread)..(tier + rule.spread) }
            if (template != null && zones.isNotEmpty()) repeat(rule.count) {
                val zone = dice.pick(zones)
                val map = factory.create(newId(), template, if (dice.chance(rule.rareChance)) Rarity.RARE else Rarity.MAGIC, dice, level = zone.level)
                map.mapZone = zone.code
                map.mapTier = loot.mapTier(zone.level, 0, dice)
                equipment += map
            }
        }
        chest.items.forEach { (code, range) ->
            val amount = if (range.isEmpty()) 0L else dice.betweenLong(range)
            if (amount > 0) items.merge(code, amount, Long::plus)
        }
        return Reward(0.0, gold, items, equipment)
    }

    /** Шаблон вещи по описанию [spec]: не уникалка, нужного слота и, для [ChestGear.classFit], под атрибуты класса. */
    private fun gear(loot: LootRoller, spec: ChestGear, chest: LootChest, level: Int, main: Set<String>, dice: Dice): ItemTemplate? {
        val slots = spec.slots.ifEmpty { chest.slots }
        val pool = index.forHero(index.templatePoolUpTo(spec.tables, level), level).filter { (template, _) ->
            !template.unique && template.slot != Slot.MAP && (slots.isEmpty() || template.slot in slots) &&
                (!spec.classFit || fits(template, main))
        }
        return loot.pick(pool, 0.0, dice)
    }

    /** Уникалка сундука: из его таблиц, его слотов, класса открывшего - для классового; поровну - для [LootChest.uniqueFlat]. */
    private fun unique(chest: LootChest, level: Int, heroClass: String, dice: Dice): ItemTemplate? {
        val pool = index.forHero(index.templatePoolUpTo(chest.uniqueTables, level + index.rules.loot.uniqueReach), level).filter { (template, _) ->
            template.rarity == Rarity.UNIQUE && (chest.slots.isEmpty() || template.slot in chest.slots) &&
                (if (chest.classUnique) template.heroClass == heroClass else template.heroClass == null || template.heroClass == heroClass)
        }
        return Tables.draw(if (chest.uniqueFlat) pool.map { Weighted(it.value, 1) } else pool, dice)
    }

    /** Оружие и броня, чьи требования - только из главных атрибутов класса. */
    private fun fits(template: ItemTemplate, main: Set<String>): Boolean {
        if (template.kind != TemplateKind.WEAPON && template.kind != TemplateKind.ARMOR) return false
        val needs = buildSet {
            if (template.requiredStrength > 0) add(STRENGTH)
            if (template.requiredDexterity > 0) add(AGILITY)
            if (template.requiredIntelligence > 0) add(INTELLECT)
        }
        return needs.isNotEmpty() && main.containsAll(needs)
    }

    /** Главные атрибуты класса: те из начальных, что не меньше девяти десятых самого большого. */
    private fun heroClassMain(heroClass: HeroClass?): Set<String> {
        val base = listOf(STRENGTH, AGILITY, INTELLECT).associateWith { heroClass?.base?.get(it) ?: 0.0 }
        val top = base.values.maxOrNull() ?: 0.0
        return if (top <= 0) base.keys else base.filterValues { it >= top * .9 }.keys
    }

    private companion object {
        const val STRENGTH = "STOCK_STRENGTH"
        const val AGILITY = "STOCK_AGILITY"
        const val INTELLECT = "STOCK_INTELLECT"
    }
}
