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
            id,
            template.code,
            actual,
            affixes.roll(template, actual, dice, influence, itemLevel),
            influence = influence,
            corrupted = template.corrupted,
            itemLevel = itemLevel,
            baseQuality = index.rules.loot.baseVariance.let { dice.between(it.min, it.max) },
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

    /**
     * Аффикс, чьё семейство ушло из пула вещи, а его группу там держит своё семейство слота с теми же эффектами (1.61.0:
     * заклинательские WAND_/SCEPTRE_ рядом с бижутерными), переезжает в него на тот же тир - старый жезл не слабеет.
     */
    private fun rehome(template: ItemTemplate, roll: Roll): Roll {
        val def = index.modifier(roll.code)?.takeIf { it.affix && !it.crafted } ?: return roll
        val pool = affixes.affixPool(template)
        if (pool.any { it.value.code == def.code }) return roll
        val home = pool.firstOrNull { (other) -> other.groupKey == def.groupKey && other.source == def.source && other.effects == def.effects }?.value ?: return roll
        return roll.copy(code = home.code, tier = if (roll.rolled) roll.tier.coerceIn(1, home.tiers.size) else roll.tier)
    }

    /** Держит ли копия дно своей редкости: волшебная и редкая - не меньше аффиксов, чем велит правило. */
    fun meetsFloor(template: ItemTemplate, item: ItemInstance): Boolean = item.rarity.fixed || item.rolls.count(affixes::isAffix) >= index.limits(item.rarity, template.slot).floor

    /** Пустой ли самоцвет или карта: обычный или без единого аффикса; уникальный самоцвет (1.31.0) несёт строки шаблона и пустым не бывает. */
    fun empty(template: ItemTemplate, item: ItemInstance): Boolean = template.slot.isJewelLike && !item.rarity.fixed && (item.rarity == Rarity.COMMON || item.rolls.none(affixes::isAffix))

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
        if (known.size != item.rolls.size) {
            item.rolls = known
            changed = true
        }
        val rehomed = item.rolls.map { rehome(template, it) }
        if (rehomed != item.rolls) {
            item.rolls = rehomed
            changed = true
        }
        // Копия без своего уровня (до 1.33.0) катилась на уровне зоны - её тиры уровнем шаблона не режутся
        val fitted = item.rolls.map { fitTier(it, if (item.itemLevel > 0) item.itemLevel else Int.MAX_VALUE) }
        if (fitted != item.rolls) {
            item.rolls = fitted
            changed = true
        }
        // Порча, что заменила имплиситы (исход IMPLICIT сферы ваал), их место заняла навсегда: дороллить их обратно - двойной бонус.
        val replaced = item.corrupted && item.rolls.any { it.code !in template.fixedCodes && index.modifier(it.code)?.source == Source.CORRUPTION }
        val fixed = template.fixedCodes.filter { code ->
            val source = index.modifier(code)?.source
            source?.permanent == true && !(replaced && source == Source.IMPLICIT)
        }
        val missing = fixed.filter { code -> item.rolls.none { it.code == code } }
        if (missing.isNotEmpty()) {
            item.rolls = item.rolls + missing.mapNotNull { affixes.rollCode(it, template.level, dice) }
            changed = true
        }
        if (affixes.ensureAffixes(template, item, dice)) changed = true
        if (template.corrupted && !item.corrupted) {
            item.corrupted = true
            changed = true
        }
        // Варианты выбора считались для прежней вещи: изменилась она - предложение снимается.
        if (changed) item.offer = emptyList()
        return changed
    }
}
