package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Armor
import features.logic.modifiers.AffixType
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierOperation
import features.logic.modifiers.ModifierScope
import features.logic.modifiers.ModifierSource
import features.logic.modifiers.ModifierTag
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ValueExpression
import features.logic.modifiers.ValueRange
import features.logic.stats.StatId

object EquipmentSeeder {

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        seedHelmets(list)

        return list
    }

    private fun seedHelmets(list: ArrayList<Equipment>) {
        // ==================== COMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 12,
                name = "Leather Cap",
                rarity = EnumRarity.COMMON,
                itemLevel = 1,
                modifierDefinitions = listOf(
                    ModifierDefinition(
                        id = "strength",
                        name = "+# Strength",
                        source = ModifierSource.PREFIX,
                        scope = ModifierScope.CHARACTER,
                        affixType = AffixType.PREFIX,
                        tiers = listOf(
                            ModifierTier(
                                tier = 1,
                                minItemLevel = 1,
                                weight = 100,
                                values = listOf(ValueRange(min = 5.0, max = 10.0))),
                            ModifierTier(
                                tier = 2,
                                minItemLevel = 10,
                                weight = 80,
                                values = listOf(ValueRange(min = 11.0, max = 20.0))),
                            ModifierTier(
                                tier = 3,
                                minItemLevel = 20,
                                weight = 60,
                                values = listOf(ValueRange(min = 21.0, max = 30.0)))
                        ),
                        tags = setOf(
                            ModifierTag("attribute"),
                            ModifierTag("strength")
                        ),
                        effects = listOf(
                            ModifierEffect.Stat(
                                stat = StatId("strength"),
                                operation = ModifierOperation.FLAT,
                                value = ValueExpression.ModifierValue()
                            )
                        )
                    )
                )
            ).apply { this.description = "A simple leather headguard." })
    }
}