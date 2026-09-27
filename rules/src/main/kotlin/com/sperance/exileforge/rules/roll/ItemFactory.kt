package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity

/**
 * Копии из шаблонов. Самоцвет и карта не бывают обычными (без аффикса они ничего не дают), фляга не
 * бывает редкой, волшебная и редкая копия никогда не пуста - одно место на все пути появления.
 */
class ItemFactory(val index: ContentIndex, val affixes: AffixRoller = AffixRoller(index)) {

    /** Редкость, под которую роллится новая копия шаблона. */
    fun rarityFor(template: ItemTemplate, wanted: Rarity): Rarity = when {
        template.slot.isJewelLike && wanted == Rarity.COMMON -> Rarity.UNCOMMON
        template.slot.isFlask && wanted == Rarity.RARE -> Rarity.UNCOMMON
        else -> wanted
    }

    fun create(id: String, template: ItemTemplate, rarity: Rarity, dice: Dice, influence: Influence? = null): ItemInstance {
        val actual = rarityFor(template, rarity)
        val item = ItemInstance(id, template.code, actual, affixes.roll(template, actual, dice, influence), influence = influence)
        affixes.ensureAffixes(template, item, dice)
        return item
    }

    /** Копия под влиянием со строкой влияния наверняка - добыча Бездны. */
    fun createInfluenced(id: String, template: ItemTemplate, rarity: Rarity, influence: Influence, dice: Dice): ItemInstance {
        val item = create(id, template, rarity, dice, influence)
        val rolls = item.rolls.toMutableList()
        if (affixes.forceInfluenced(template, item.rarity, rolls, influence, dice)) item.rolls = rolls
        return item
    }

    /** Держит ли копия дно своей редкости: волшебная и редкая - не меньше аффиксов, чем велит правило. */
    fun meetsFloor(template: ItemTemplate, item: ItemInstance): Boolean =
        item.rarity.fixed || item.rolls.count(affixes::isAffix) >= index.limits(item.rarity, template.slot).floor

    /** Пустой ли самоцвет или карта: обычный или без единого аффикса. */
    fun empty(template: ItemTemplate, item: ItemInstance): Boolean =
        template.slot.isJewelLike && (item.rarity == Rarity.COMMON || item.rolls.none(affixes::isAffix))

    /**
     * Сверка старой копии с шаблоном: пропавшие закреплённые описания уходят, новые закреплённые
     * дороллены, волшебная и редкая доведена до дна. True - копия изменилась.
     */
    fun reconcile(template: ItemTemplate, item: ItemInstance, dice: Dice): Boolean {
        var changed = false
        val known = item.rolls.filter { index.modifier(it.code) != null }
        if (known.size != item.rolls.size) { item.rolls = known; changed = true }
        val fixed = template.fixedCodes.filter { code -> index.modifier(code)?.source?.permanent == true }
        val missing = fixed.filter { code -> item.rolls.none { it.code == code } }
        if (missing.isNotEmpty()) {
            item.rolls = item.rolls + missing.mapNotNull { affixes.rollCode(it, template.level, dice) }
            changed = true
        }
        if (affixes.ensureAffixes(template, item, dice)) changed = true
        return changed
    }
}
