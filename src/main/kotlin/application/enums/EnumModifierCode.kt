package application.enums

/**
 * Реестр кодов модификаторов, которыми оперирует сидер.
 *
 * Сами описания модификаторов (стат, операция, источник) и их тиры
 * лежат в коллекциях Mongo `ModifierDefinition` и `ModifierTier`.
 * Здесь остаются только стабильные ключи, по которым сидер связывает
 * предметы с уже созданными документами модификаторов.
 */
enum class EnumModifierCode {
    PREFIX_ADD_STRENGTH,
    PREFIX_ADD_HEALTH,
    PREFIX_ADD_ARMOR,
    PREFIX_ADD_MANA,
    PREFIX_ADD_AGILITY,

    SUFFIX_ADD_STRENGTH,
    SUFFIX_ADD_HEALTH,
    SUFFIX_ADD_ARMOR,
    SUFFIX_ADD_MANA,
    SUFFIX_ADD_AGILITY
}
