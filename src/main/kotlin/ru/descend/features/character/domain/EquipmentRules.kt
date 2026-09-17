package ru.descend.features.character.domain

import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.features.character.model.*
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.poe.catalog.*
import ru.descend.shared.http.invalid
import kotlinx.serialization.json.JsonObject

object EquipmentRules {
    fun validate(character: Character, templates: Map<String, Equipment>, catalog: PoeCatalog, calculate: (Character) -> CharacterStats) {
        if (character.equipped.values.size != character.equipped.values.toSet().size) invalid("An item cannot occupy multiple slots")
        fun template(uuid: String): Equipment {
            val item = character.equipments.singleOrNull { it.uuid == uuid } ?: invalid("Item is not in inventory")
            return item.baseSnapshot ?: templates[item.equipmentId] ?: invalid("Missing equipment base")
        }
        val main = character.equipped[EquipmentSlot.MAIN_HAND]?.let(::template)
        val off = character.equipped[EquipmentSlot.OFF_HAND]?.let(::template)
        if (main?.slot == EnumEquipmentType.WEAPON_2H && off != null && !(off.slot == EnumEquipmentType.QUIVER && (main as? ru.descend.features.equipment.model.Weapon)?.weaponType == ru.descend.domain.enums.EnumEquipmentWeapon.BOW)) invalid("Two-handed weapon blocks off-hand")
        if (off?.slot == EnumEquipmentType.QUIVER && (main as? ru.descend.features.equipment.model.Weapon)?.weaponType != ru.descend.domain.enums.EnumEquipmentWeapon.BOW) invalid("Quiver requires a bow")
        character.equipped.forEach { (slot, uuid) ->
            val item = template(uuid)
            val fits = when (slot) {
                EquipmentSlot.MAIN_HAND -> item.slot in setOf(EnumEquipmentType.WEAPON_1H, EnumEquipmentType.WEAPON_2H)
                EquipmentSlot.OFF_HAND -> item.slot in setOf(EnumEquipmentType.WEAPON_1H, EnumEquipmentType.SHIELD, EnumEquipmentType.QUIVER)
                EquipmentSlot.RING_LEFT, EquipmentSlot.RING_RIGHT -> item.slot == EnumEquipmentType.RING
                else -> item.slot.name == slot.name
            }
            if (!fits) invalid("Item does not fit the selected slot")
            val state = character.equipments.single { it.uuid == uuid }.poe
            val base = state?.baseId?.let { catalog.bases[it] }
            val req = base?.get("requirements") as? JsonObject
            val requiredLevel = maxOf(req?.int("level", 1) ?: 1, if (state?.rarity == ru.descend.features.poe.domain.PoeRarity.UNIQUE) base?.int("drop_level", 1) ?: 1 else 1)
            if (character.level < requiredLevel) invalid("Character level is too low")
            // The item cannot satisfy its own attribute requirements.
            val without = calculate(character.copy(equipped = character.equipped - slot)).values
            for (attr in listOf("strength", "dexterity", "intelligence")) if ((without[attr] ?: 0.0) < (req?.int(attr) ?: 0)) invalid("Insufficient $attr for equipment")
        }
    }
}
