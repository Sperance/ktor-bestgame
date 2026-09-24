package features.logic.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.logic.modifiers.ModifierRoller

/**
 * Самоцвет не бывает пустым (с 0.42.0): у него нет базы, и без аффикса он ничего не даёт. Поэтому
 * обычным он не падает, не куётся и не выдаётся - минимум волшебным, - и сфера, после которой он
 * остался бы без аффикса, отказывает. С 0.45.0 то же правило у карты: она минимум волшебная и хоть
 * с одним модификатором.
 */
object Jewels {

    fun isJewel(template: Equipment): Boolean = template.slot == EnumEquipmentType.JEWEL || template.slot == EnumEquipmentType.MAP

    /** Редкость, под которую роллится новая копия: обычный самоцвет становится волшебным. */
    fun rarity(template: Equipment, rarity: EnumRarity): EnumRarity =
        if (isJewel(template) && rarity == EnumRarity.COMMON) EnumRarity.UNCOMMON else rarity

    /** Пустой ли самоцвет: обычный или без единого аффикса. */
    fun empty(template: Equipment, item: CharacterEquipment): Boolean =
        isJewel(template) && (item.rarity == EnumRarity.COMMON || item.params.none { ModifierRoller.isAffix(it) })

    /**
     * Чинит пустой самоцвет, лежавший до 0.42.0: волшебным и с аффиксами заново. Отвечает, изменился ли он.
     */
    fun repair(template: Equipment, item: CharacterEquipment): Boolean {
        if (!empty(template, item)) return false
        item.rarity = rarity(template, item.rarity)
        val permanent = item.params.filterNot { ModifierRoller.isAffix(it) }
        item.params = (permanent + ModifierRoller.rollAffixes(template, item.rarity, item.influence)).toMutableList()
        return true
    }
}
