package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.Weighted
import com.sperance.exileforge.rules.text.LocaleKey

/**
 * Выбор строки (1.65.0), как раскрытие скрытого модификатора ([Veils]): сфера алхимии или возвышения со знамением выбора
 * кладёт на копию варианты [ItemInstance.offer], игрок берёт один. Выбор проверяется заново: копия могла измениться, и вариант,
 * которому больше нет места, не встаёт.
 */
class Choices(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index)) {

    /** Строки «Алхимия» карты, которых на ней ещё нет, - пока их меньше потолка; иначе пусто. */
    fun mapAlchemyPool(item: ItemInstance): List<Weighted<ModifierDef>> {
        val present = affixes.definitions(item.rolls).filter { it.source == Source.ALCHEMY }
        if (present.size >= index.rules.orbs.maxAlchemyLines) return emptyList()
        val codes = present.mapTo(HashSet()) { it.code }
        return index.modifierPool(listOf(index.rules.orbs.mapAlchemy)).filter { it.value.code !in codes }
    }

    fun choose(item: ItemInstance, template: ItemTemplate, choice: Int): OrbOutcome {
        val name = LocaleKey.equipmentName(template.code)
        val option = item.offer.getOrNull(choice) ?: throw RuleViolation("CR_034", listOf(name))
        val def = affixes.definition(option) ?: throw RuleViolation("CR_034", listOf(name))
        if (def.source == Source.ALCHEMY) {
            if (mapAlchemyPool(item).none { it.value.code == def.code }) throw RuleViolation("CR_022", listOf(name))
        } else {
            val limits = index.limits(item.rarity, template.slot)
            if (item.rarity.fixed || affixes.affixes(item.rolls).size >= limits.ceiling) throw RuleViolation("CR_007", listOf(name))
            val (prefixes, suffixes) = affixes.freeSlots(item.rarity, affixes.definitions(item.rolls), template.slot)
            if ((if (def.source == Source.PREFIX) prefixes else suffixes) <= 0) throw RuleViolation("CR_007", listOf(name))
            if (!affixes.fits(def, affixes.groups(item.rolls))) throw RuleViolation("CR_016", listOf(name))
        }
        item.rolls = item.rolls + option
        item.offer = emptyList()
        return OrbOutcome(item, null, "currency.chosen", listOf(name))
    }
}
