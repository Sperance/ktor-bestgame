package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Source

/**
 * Копии из шаблонов. Самоцвет и карта не бывают обычными (без аффикса они ничего не дают), фляга не
 * бывает редкой, волшебная и редкая копия никогда не пуста - одно место на все пути появления. Уникальный
 * самоцвет (1.31.0) - уникалка как все: редкость закреплена шаблоном, строки - его.
 */
class ItemFactory(val index: ContentIndex, val affixes: AffixRoller = AffixRoller(index)) {

    /** Редкость, под которую роллится новая копия шаблона. */
    fun rarityFor(template: ItemTemplate, wanted: Rarity): Rarity = when {
        template.slot.isJewelLike && wanted == Rarity.COMMON -> Rarity.MAGIC
        template.slot.isFlask && wanted == Rarity.RARE -> Rarity.MAGIC
        else -> wanted
    }

    /** Новая копия на уровне предмета [level] (1.33.0): добыча - уровень зоны с прибавками, прочее - уровень героя. */
    fun create(id: String, template: ItemTemplate, rarity: Rarity, dice: Dice, influence: Influence? = null, level: Int = template.level): ItemInstance {
        val actual = rarityFor(template, rarity)
        val itemLevel = level.coerceIn(1, index.rules.loot.maxItemLevel)
        val item = ItemInstance(
            id, template.code, actual, affixes.roll(template, actual, dice, influence, itemLevel),
            influence = influence, corrupted = template.corrupted, itemLevel = itemLevel,
        )
        affixes.ensureAffixes(template, item, dice)
        return item
    }

    /** Копия под влиянием со строкой влияния наверняка - добыча Бездны. */
    fun createInfluenced(id: String, template: ItemTemplate, rarity: Rarity, influence: Influence, dice: Dice, level: Int = template.level): ItemInstance {
        val item = create(id, template, rarity, dice, influence, level)
        val rolls = item.rolls.toMutableList()
        if (affixes.forceInfluenced(template, item.rarity, rolls, influence, dice, item.itemLevel)) item.rolls = rolls
        return item
    }

    /**
     * Тир старого ролла по нынешней лестнице (1.57.0): номер за её концом - худший тир; аффикс или строка эссенции на тире,
     * что уровень вещи [level] ещё не открывает (лестница стала длиннее, вершина эссенции), - лучший открытый. Верстачные не трогаются.
     */
    private fun fitTier(roll: Roll, level: Int): Roll {
        val def = index.modifier(roll.code) ?: return roll
        if (!roll.rolled || def.tiers.isEmpty() || def.crafted) return roll
        val within = roll.tier.coerceIn(1, def.tiers.size)
        val open = if (def.affix || def.source == Source.ESSENCE) def.bestTierAt(level)?.first ?: within else within
        val tier = maxOf(within, open)
        return if (tier == roll.tier) roll else roll.copy(tier = tier)
    }

    /** Держит ли копия дно своей редкости: волшебная и редкая - не меньше аффиксов, чем велит правило. */
    fun meetsFloor(template: ItemTemplate, item: ItemInstance): Boolean =
        item.rarity.fixed || item.rolls.count(affixes::isAffix) >= index.limits(item.rarity, template.slot).floor

    /** Пустой ли самоцвет или карта: обычный или без единого аффикса; уникальный самоцвет (1.31.0) несёт строки шаблона и пустым не бывает. */
    fun empty(template: ItemTemplate, item: ItemInstance): Boolean =
        template.slot.isJewelLike && !item.rarity.fixed && (item.rarity == Rarity.COMMON || item.rolls.none(affixes::isAffix))

    /**
     * Сверка старой копии с шаблоном: недопустимая редкость (обычный самоцвет или карта, редкая фляга)
     * исправлена первой - лишние аффиксы сняты по местам новой редкости, - пропавшие закреплённые описания
     * уходят, новые закреплённые дороллены, волшебная и редкая доведена до дна. True - копия изменилась.
     */
    fun reconcile(template: ItemTemplate, item: ItemInstance, dice: Dice): Boolean {
        var changed = false
        val rarity = rarityFor(template, item.rarity)
        if (rarity != item.rarity) {
            item.rarity = rarity
            affixes.normalize(template, rarity, item.rolls, dice, item.influence, item.level(template))?.let { item.rolls = it }
            changed = true
        }
        val known = item.rolls.filter { index.modifier(it.code) != null }
        if (known.size != item.rolls.size) { item.rolls = known; changed = true }
        // Копия без своего уровня (до 1.33.0) катилась на уровне зоны - её тиры уровнем шаблона не режутся
        val fitted = item.rolls.map { fitTier(it, if (item.itemLevel > 0) item.itemLevel else Int.MAX_VALUE) }
        if (fitted != item.rolls) { item.rolls = fitted; changed = true }
        val fixed = template.fixedCodes.filter { code -> index.modifier(code)?.source?.permanent == true }
        val missing = fixed.filter { code -> item.rolls.none { it.code == code } }
        if (missing.isNotEmpty()) {
            item.rolls = item.rolls + missing.mapNotNull { affixes.rollCode(it, template.level, dice) }
            changed = true
        }
        if (affixes.ensureAffixes(template, item, dice)) changed = true
        if (template.corrupted && !item.corrupted) { item.corrupted = true; changed = true }
        return changed
    }
}
