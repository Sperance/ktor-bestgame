package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.AbyssRule
import com.sperance.exileforge.rules.content.AtlasBonuses
import com.sperance.exileforge.rules.content.ChestRule
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.CrystalRule
import com.sperance.exileforge.rules.content.EssenceBook
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.RarityRule
import com.sperance.exileforge.rules.content.VaalRule
import com.sperance.exileforge.rules.content.tenths
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor

/** Окно сундуков зоны у героя: [left] ещё открыть до [refreshAt] (мс эпохи). */
@Serializable
data class ChestWindow(val refreshAt: Long = 0, val left: Int = 0)

object Chests {
    /** Окно на момент [now]: живое как есть, истёкшее бросается заново с сундуками героя и атласа. */
    fun window(current: ChestWindow?, now: Long, rule: ChestRule, bonus: Double, extra: Int, dice: Dice): ChestWindow {
        if (current != null && now < current.refreshAt) return current
        val base = dice.between(rule.count)
        val share = bonus.coerceAtLeast(0.0) / 100
        val whole = floor(share).toInt()
        val count = (base + whole + extra + if (dice.chance(share - whole)) 1 else 0).coerceAtLeast(0)
        return ChestWindow(now + (rule.refreshHours * 3_600_000).toLong(), count)
    }

    /** Редкость, с которой катается добыча сундука: множитель количества и бонус редкости правила. */
    fun rarity(rule: ChestRule): RarityRule = RarityRule(MonsterRarity.NORMAL, quantity = rule.quantity, rarityBonus = rule.rarityBonus)
}

/** Ваал-зона за порталом: её строки (роллы со сдвигом силы зоны), сложенные эффекты и что она платит. */
@Serializable
data class VaalZone(val mapCode: String, val level: Int, val rolls: List<Roll>, val effects: Map<String, Double>, val quantity: Double, val rarity: Double, val experience: Double)

class VaalZones(private val index: ContentIndex) {
    fun roll(mapCode: String, level: Int, dice: Dice, atlas: AtlasBonuses = AtlasBonuses.NONE): VaalZone {
        val rule: VaalRule = index.campaign.vaal
        val pool = index.modifierPool(listOf(rule.pool)).filter { it.value.rolls }.toMutableList()
        val high = rule.mods.getOrElse(1) { 8 }
        val low = (rule.mods.getOrElse(0) { 3 } + atlas.vaalMinMods).coerceIn(0, high)
        val count = dice.between(low, high).coerceAtMost(pool.size)
        val rolls = List(count) {
            val picked = Tables.draw(pool, dice)!!
            pool.removeAll { it.value === picked }
            val (number, _) = picked.bestTierAt(level) ?: (1 to picked.tiers.first())
            Roll(picked.code, number, dice.share(), scale = rule.power)
        }
        val effects = mutableMapOf<String, Double>()
        rolls.forEach { roll ->
            val def = index.modifier(roll.code) ?: return@forEach
            val values = roll.values(def)
            def.effects.forEachIndexed { i, effect -> effects.merge(effect.stat, values.getOrElse(i) { 0.0 }, Double::plus) }
        }
        val risk = LootRoller(index).risk(effects) * rule.reward
        val bonus = tenths((risk + rule.perMod * rolls.size) * (1 + atlas.vaalReward / 100))
        return VaalZone(mapCode, level, rolls, effects, bonus, bonus, tenths(risk))
    }
}

/** Кристалл эссенций зоны: коды эссенций, страж (монстр зоны, встающий редким), усилен ли и прошла ли по нему сфера Ваал. */
@Serializable
data class Crystal(val essences: List<String>, val guardian: String, val stronger: Boolean = false, val vaal: Boolean = false)

@Serializable
data class CrystalWindow(val refreshAt: Long = 0, val crystals: List<Crystal> = emptyList())

class EssenceCrystals(private val index: ContentIndex) {
    private val book: EssenceBook get() = index.essences

    fun window(current: CrystalWindow?, now: Long, level: Int, monsters: List<String>, dice: Dice, atlas: AtlasBonuses): CrystalWindow {
        val rule = book.crystals
        if (current != null && now < current.refreshAt) return current
        val base = dice.between(rule.count) + (if (dice.percent(atlas.crystalChance)) 1 else 0) + atlas.crystals
        val scaled = base * (1 + atlas.crystalsMore / 100)
        val count = floor(scaled).toInt() + if (dice.chance(scaled - floor(scaled))) 1 else 0
        return CrystalWindow(now + (rule.refreshHours * 3_600_000).toLong(), List(count.coerceAtLeast(0)) { roll(level, monsters, dice, atlas) })
    }

    fun roll(level: Int, monsters: List<String>, dice: Dice, atlas: AtlasBonuses): Crystal {
        val rule: CrystalRule = book.crystals
        val zone = book.tierOf(level)
        val count = dice.between(rule.essences) + if (dice.percent(atlas.crystalEssences)) 1 else 0
        val essences = List(count) {
            val lower = if (dice.chance(rule.lowerChance)) 1 else 0
            val higher = if (dice.percent(atlas.crystalTier)) 1 else 0
            EssenceBook.code(dice.pick(book.kinds).code, (zone - lower + higher).coerceIn(1, book.tiers.size), false)
        }
        return Crystal(essences, dice.pick(monsters))
    }

    /** Сфера Ваал на кристалле: по таблице исходов - все эссенции выше, одна особая или страж сильнее. */
    fun vaal(crystal: Crystal, dice: Dice): Pair<String, Crystal> {
        val outcome = Tables.draw(index.tables.pool(listOf(book.crystals.vaal)), dice) ?: EssenceBook.VAAL_STRONGER
        val changed = when (outcome) {
            EssenceBook.VAAL_UPGRADE -> crystal.copy(essences = crystal.essences.map { code ->
                val essence = book.essences[code]
                if (essence == null || essence.special) code else EssenceBook.code(essence.kind.code, (essence.tier + 1).coerceAtMost(book.tiers.size), false)
            })
            EssenceBook.VAAL_SPECIAL -> crystal.copy(essences = crystal.essences.toMutableList().also { it[dice.nextInt(it.size)] = EssenceBook.code(dice.pick(book.specials).code, 0, true) })
            else -> crystal.copy(stronger = true)
        }
        return outcome to changed.copy(vaal = true)
    }
}

/** Окно расщелин Бездны у героя: до [refreshAt] стоят те, что остались, - по глубине каждой. */
@Serializable data class AbyssWindow(val refreshAt: Long = 0, val cracks: List<Int> = emptyList())

/** Открытый спуск: зона и сколько ступеней в нём. */
@Serializable data class AbyssRun(val mapCode: String, val depth: Int)

/** Копилка, какой её видит герой. */
@Serializable data class AbyssHoardView(val items: List<Int>, val rare: Double, val orbs: List<Int>, val unique: Double, val experience: Double)

data class HoardBonus(val items: Double = 1.0, val rare: Double = 0.0, val orbs: Double = 1.0, val unique: Double = 1.0, val experience: Double = 0.0)
data class HoardRoll(val items: List<Rarity>, val orbs: Map<String, Long>, val unique: Boolean, val experience: Double) {
    companion object { val EMPTY = HoardRoll(emptyList(), emptyMap(), false, 0.0) }
}

class AbyssRifts(private val index: ContentIndex) {
    fun window(current: AbyssWindow?, now: Long, rule: AbyssRule, level: Int, dice: Dice, atlas: AtlasBonuses): AbyssWindow {
        if (current != null && now < current.refreshAt) return current
        val refreshAt = now + (rule.refreshHours * 3_600_000).toLong()
        if (level < rule.minLevel || !dice.percent(rule.chance + atlas.abyssChance)) return AbyssWindow(refreshAt)
        val count = 1 + if (dice.percent(atlas.abyssExtra)) 1 else 0
        return AbyssWindow(refreshAt, List(count) { crack(rule, dice, atlas) })
    }

    fun crack(rule: AbyssRule, dice: Dice, atlas: AtlasBonuses): Int = (dice.between(rule.depth) + atlas.abyssDepth).coerceIn(1, rule.waves.size)
    fun depth(rule: AbyssRule, crack: Int, map: Double): Int = (crack + map.toInt()).coerceIn(1, rule.waves.size)

    fun view(rule: AbyssRule, depth: Int, bonus: HoardBonus): AbyssHoardView {
        val hoard = rule.hoard[depth - 1]
        fun range(pair: List<Int>, scale: Double) = listOf(floor(pair[0] * scale).toInt(), ceil(pair[1] * scale).toInt())
        return AbyssHoardView(range(hoard.items, bonus.items), (hoard.rare + bonus.rare).coerceIn(0.0, 100.0), range(hoard.orbs, bonus.orbs), (hoard.unique * bonus.unique).coerceIn(0.0, 100.0), Math.round(hoard.experience * bonus.experience).toDouble())
    }

    fun roll(rule: AbyssRule, depth: Int, bonus: HoardBonus, keep: Double, dice: Dice): HoardRoll {
        if (depth < 1 || keep <= 0) return HoardRoll.EMPTY
        val hoard = rule.hoard[depth - 1]
        val share = keep.coerceAtMost(1.0)
        val rare = (hoard.rare + bonus.rare).coerceIn(0.0, 100.0)
        val items = List(dice.times(dice.between(hoard.items) * bonus.items * share)) { if (dice.percent(rare)) Rarity.RARE else Rarity.UNCOMMON }
        val orbPool = index.tables.pool(listOf(rule.orbs))
        val orbs = mutableMapOf<String, Long>()
        repeat(dice.times(dice.between(hoard.orbs) * bonus.orbs * share)) { Tables.draw(orbPool, dice)?.let { orbs.merge(it, 1L, Long::plus) } }
        val unique = dice.percent(hoard.unique * bonus.unique * share)
        return HoardRoll(items, orbs, unique, Math.round(hoard.experience * bonus.experience * share).toDouble())
    }
}
