package application.enums

import features.logic.modifiers.ModifierDefinition

enum class EnumModifierDefinitions(val definition: ModifierDefinition) {
    PREFIX_ADD_STRENGTH(ModifierDefinition(stat = EnumStatStock.STOCK_STRENGTH, operation = EnumModifierOperation.ADD, source = EnumModifierSource.PREFIX, stepValue = 4.0)),
    PREFIX_ADD_HEALTH(ModifierDefinition(stat = EnumStatStock.STOCK_HEALTH, operation = EnumModifierOperation.ADD, source = EnumModifierSource.PREFIX, stepValue = 34.0)),
    PREFIX_ADD_ARMOR(ModifierDefinition(stat = EnumStatStock.STOCK_ARMOR, operation = EnumModifierOperation.ADD, source = EnumModifierSource.PREFIX)),
    PREFIX_ADD_MANA(ModifierDefinition(stat = EnumStatStock.STOCK_MANA, operation = EnumModifierOperation.ADD, source = EnumModifierSource.PREFIX, stepValue = 23.0)),
    PREFIX_ADD_AGILITY(ModifierDefinition(stat = EnumStatStock.STOCK_AGILITY, operation = EnumModifierOperation.ADD, source = EnumModifierSource.PREFIX, stepValue = 4.0)),

    SUFFIX_ADD_STRENGTH(ModifierDefinition(stat = EnumStatStock.STOCK_STRENGTH, operation = EnumModifierOperation.ADD, source = EnumModifierSource.SUFFIX, stepValue = 4.0)),
    SUFFIX_ADD_HEALTH(ModifierDefinition(stat = EnumStatStock.STOCK_HEALTH, operation = EnumModifierOperation.ADD, source = EnumModifierSource.SUFFIX, stepValue = 34.0)),
    SUFFIX_ADD_ARMOR(ModifierDefinition(stat = EnumStatStock.STOCK_ARMOR, operation = EnumModifierOperation.ADD, source = EnumModifierSource.SUFFIX)),
    SUFFIX_ADD_MANA(ModifierDefinition(stat = EnumStatStock.STOCK_MANA, operation = EnumModifierOperation.ADD, source = EnumModifierSource.SUFFIX, stepValue = 23.0)),
    SUFFIX_ADD_AGILITY(ModifierDefinition(stat = EnumStatStock.STOCK_AGILITY, operation = EnumModifierOperation.ADD, source = EnumModifierSource.SUFFIX, stepValue = 4.0));

    fun getDefinitionsType(type: EnumModifierSource): List<EnumModifierDefinitions> {
        return entries.filter { it.definition.source == type }
    }
}