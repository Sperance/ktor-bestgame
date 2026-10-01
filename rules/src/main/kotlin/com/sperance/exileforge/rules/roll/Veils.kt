package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.text.LocaleKey

/**
 * Скрытые модификаторы (1.35.0), как Veiled в PoE: редкий монстр со строкой `MOB_VEILED` роняет волшебную или редкую
 * вещь (с 1.65.0 и инструмент), чей аффикс скрыт; сфера раскрытия предлагает [com.sperance.exileforge.rules.content.QualityRules.unveilChoices]
 * гибридов из таблицы `veiled:<сторона>`, игрок выбирает один - он встаёт на место скрытого.
 */
class Veils(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index)) {

    fun veiled(item: ItemInstance): Roll? = item.rolls.firstOrNull { it.code == PREFIX || it.code == SUFFIX }

    /** Прячет аффикс на выпавшей копии: на свободное место или вместо случайного; true - копия изменилась. */
    fun veil(template: ItemTemplate, item: ItemInstance, dice: Dice): Boolean {
        if (item.rarity != Rarity.MAGIC && item.rarity != Rarity.RARE) return false
        if (template.slot.isJewelLike || template.slot.isFlask || veiled(item) != null) return false
        val (prefixes, suffixes) = affixes.freeSlots(item.rarity, affixes.definitions(item.rolls), template.slot)
        val free = listOfNotNull(Source.PREFIX.takeIf { prefixes > 0 }, Source.SUFFIX.takeIf { suffixes > 0 })
        val replaced = if (free.isEmpty()) affixes.affixes(item.rolls).filterNot { it.fractured }.takeIf { it.isNotEmpty() }?.let(dice::pick) else null
        val side = if (free.isNotEmpty()) dice.pick(free) else replaced?.let { affixes.definition(it)?.source } ?: return false
        val veil = Roll(if (side == Source.PREFIX) PREFIX else SUFFIX, 1, 0.0)
        item.rolls = (if (replaced != null) item.rolls - replaced else item.rolls) + veil
        return true
    }

    /**
     * Варианты раскрытия скрытого аффикса [item]: разные гибриды его стороны, открытые уровнем копии (1.61.0), чьи группы ([ContentIndex.groups])
     * не заняты остальными строками копии. Все гибриды стороны заняты - обычные аффиксы этой стороны из таблиц копии, так что
     * скрытый аффикс раскрывается всегда. Ждущий выбор, который строки копии с тех пор перекрыли целиком, предлагается заново.
     */
    fun offer(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val veil = veiled(item) ?: throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        val taken = affixes.groups(item.rolls.filterNot { it === veil })
        if (item.unveil.any { fits(it, taken) }) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        val side = if (veil.code == PREFIX) Source.PREFIX else Source.SUFFIX
        val level = item.level(template)
        val pool = index.modifierPool(listOf(table(template, side))).filter { it.value.openAt(level) && affixes.fits(it.value, taken) }
            .ifEmpty { affixes.affixPool(template, item.influence).filter { it.value.source == side && it.value.openAt(level) && affixes.fits(it.value, taken) } }
            .toMutableList()
        val options = mutableListOf<Roll>()
        while (options.size < index.rules.quality.unveilChoices && pool.isNotEmpty()) {
            val def = Tables.draw(pool, dice) ?: break
            pool.removeAll { it.value.code == def.code }
            affixes.roll(def, level, dice)?.let(options::add)
        }
        if (options.isEmpty()) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        item.unveil = options
        return OrbOutcome(item, null, "currency.unveil_offer", listOf(LocaleKey.equipmentName(template.code), options.size.toString()))
    }

    /** Выбор игрока [choice] встаёт на место скрытого аффикса; вариант, чью группу копия с тех пор заняла, не встаёт. */
    fun reveal(item: ItemInstance, template: ItemTemplate, choice: Int): OrbOutcome {
        val veil = veiled(item)
        val option = item.unveil.getOrNull(choice)
        if (veil == null || option == null) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        if (!fits(option, affixes.groups(item.rolls.filterNot { it === veil }))) throw RuleViolation("CR_016", listOf(LocaleKey.equipmentName(template.code)))
        item.rolls = item.rolls.map { if (it === veil) option else it }
        item.unveil = emptyList()
        return OrbOutcome(item, null, "currency.unveiled", listOf(LocaleKey.equipmentName(template.code)))
    }

    /** Гибриды стороны: у инструмента (1.65.0) - свои, строк труда (`veiled:tool:<сторона>`). */
    private fun table(template: ItemTemplate, side: Source): String =
        listOfNotNull(TABLE, template.slot.tag.takeIf { template.slot.isTool }, side.name.lowercase()).joinToString(":")

    private fun fits(option: Roll, taken: Set<String>): Boolean = affixes.definition(option)?.let { affixes.fits(it, taken) } == true

    companion object {
        const val PREFIX = "VEILED_PREFIX"
        const val SUFFIX = "VEILED_SUFFIX"
        const val TABLE = "veiled"
        /** Стат строки монстра, чья добыча несёт скрытый аффикс. */
        const val LOOT = "STOCK_VEILED_LOOT"
    }
}
