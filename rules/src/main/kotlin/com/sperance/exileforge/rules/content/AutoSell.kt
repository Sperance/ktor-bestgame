package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.roll.ItemInstance
import kotlinx.serialization.Serializable

/** Группа слотов фильтра добычи: так игрок и думает о вещах - оружие, броня, бижутерия, фляги, самоцветы, карты. */
@Serializable
enum class SlotGroup {
    WEAPON, ARMOUR, JEWELLERY, FLASK, JEWEL, MAP;

    companion object {
        fun of(slot: Slot): SlotGroup? = when {
            slot.isWeapon || slot == Slot.QUIVER -> WEAPON
            slot.isArmour -> ARMOUR
            slot == Slot.RING || slot == Slot.RING_2 || slot == Slot.AMULET || slot == Slot.BELT || slot == Slot.COLLAR -> JEWELLERY
            slot.isFlask -> FLASK
            slot == Slot.JEWEL -> JEWEL
            slot == Slot.MAP -> MAP
            else -> null
        }
    }
}

/**
 * Фильтр добычи (1.45.0): какие редкости в каких группах слотов торговец забирает сразу, не кладя в тайник.
 * Продаются только обычные, волшебные и редкие; уникальная, влиянием тронутая, осквернённая, с расколотой
 * строкой или запертая вещь не продаётся никогда - что бы ни велел фильтр.
 */
@Serializable
data class AutoSell(val sell: Map<Rarity, Set<SlotGroup>> = emptyMap()) {

    fun sells(template: ItemTemplate, item: ItemInstance): Boolean =
        item.rarity in SELLABLE && !item.locked && !item.corrupted && item.influence == null && item.rolls.none { it.fractured } &&
            SlotGroup.of(template.slot)?.let { it in sell[item.rarity].orEmpty() } == true

    /** Фильтр с новой строкой редкости [rarity]: пустой набор групп убирает её. */
    fun with(rarity: Rarity, groups: Set<SlotGroup>): AutoSell = copy(sell = if (groups.isEmpty()) sell - rarity else sell + (rarity to groups))

    companion object {
        val SELLABLE = setOf(Rarity.COMMON, Rarity.MAGIC, Rarity.RARE)
    }
}
