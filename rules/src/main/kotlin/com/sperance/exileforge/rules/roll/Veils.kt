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
 * вещь, чей аффикс скрыт; сфера раскрытия предлагает [com.sperance.exileforge.rules.content.QualityRules.unveilChoices]
 * гибридов из таблицы `veiled:<сторона>`, игрок выбирает один - он встаёт на место скрытого.
 */
class Veils(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index)) {

    fun veiled(item: ItemInstance): Roll? = item.rolls.firstOrNull { it.code == PREFIX || it.code == SUFFIX }

    /** Прячет аффикс на выпавшей копии: на свободное место или вместо случайного; true - копия изменилась. */
    fun veil(template: ItemTemplate, item: ItemInstance, dice: Dice): Boolean {
        if (item.rarity != Rarity.MAGIC && item.rarity != Rarity.RARE) return false
        if (template.slot.isJewelLike || template.slot.isFlask || template.slot.isTool || veiled(item) != null) return false
        val (prefixes, suffixes) = affixes.freeSlots(item.rarity, affixes.definitions(item.rolls), template.slot)
        val free = listOfNotNull(Source.PREFIX.takeIf { prefixes > 0 }, Source.SUFFIX.takeIf { suffixes > 0 })
        val replaced = if (free.isEmpty()) affixes.affixes(item.rolls).filterNot { it.fractured }.takeIf { it.isNotEmpty() }?.let(dice::pick) else null
        val side = if (free.isNotEmpty()) dice.pick(free) else replaced?.let { affixes.definition(it)?.source } ?: return false
        val veil = Roll(if (side == Source.PREFIX) PREFIX else SUFFIX, 1, 0.0)
        item.rolls = (if (replaced != null) item.rolls - replaced else item.rolls) + veil
        return true
    }

    /** Варианты раскрытия скрытого аффикса [item]: разные гибриды его стороны на уровне копии. */
    fun offer(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val veil = veiled(item) ?: throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        if (item.unveil.isNotEmpty()) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        val pool = index.modifierPool(listOf(if (veil.code == PREFIX) "$TABLE:prefix" else "$TABLE:suffix")).toMutableList()
        val options = mutableListOf<Roll>()
        while (options.size < index.rules.quality.unveilChoices && pool.isNotEmpty()) {
            val def = Tables.draw(pool, dice) ?: break
            pool.removeAll { it.value.code == def.code }
            affixes.roll(def, item.level(template), dice)?.let(options::add)
        }
        if (options.isEmpty()) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        item.unveil = options
        return OrbOutcome(item, null, "currency.unveil_offer", listOf(LocaleKey.equipmentName(template.code), options.size.toString()))
    }

    /** Выбор игрока [choice] встаёт на место скрытого аффикса. */
    fun reveal(item: ItemInstance, template: ItemTemplate, choice: Int): OrbOutcome {
        val veil = veiled(item)
        val option = item.unveil.getOrNull(choice)
        if (veil == null || option == null) throw RuleViolation("CR_032", listOf(LocaleKey.equipmentName(template.code)))
        item.rolls = item.rolls.map { if (it === veil) option else it }
        item.unveil = emptyList()
        return OrbOutcome(item, null, "currency.unveiled", listOf(LocaleKey.equipmentName(template.code)))
    }

    companion object {
        const val PREFIX = "VEILED_PREFIX"
        const val SUFFIX = "VEILED_SUFFIX"
        const val TABLE = "veiled"
        /** Стат строки монстра, чья добыча несёт скрытый аффикс. */
        const val LOOT = "STOCK_VEILED_LOOT"
    }
}
