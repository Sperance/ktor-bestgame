package application.enums

enum class EnumEquipmentType {
    HELMET,
    BODY,
    GLOVES,
    RING,
    BOOTS,
    WINGS,
    BELT,
    WEAPON_1H,
    WEAPON_2H,
    QUIVER,
    SHIELD,
    AMULET,

    /**
     * Второе кольцо. Шаблона с таким слотом не бывает: это только значение
     * `CharacterEquipment.equippedSlot`, куда встаёт второе надетое кольцо.
     */
    RING_2,

    /**
     * Самоцвет. Слот у него условный: носится он не на персонаже, а в гнезде
     * дерева навыков, и `CharacterEquipment.equippedSlot` хранит код узла-гнезда,
     * а не имя слота. Гнёзд много, поэтому самоцветов надето может быть несколько.
     */
    JEWEL,

    /**
     * Карта (с 0.35.0): не надевается, а кладётся в окно запуска локации своего уровня и сгорает
     * при запуске. Её модификаторы меняют заход - монстров, героя и награды.
     */
    MAP,

    /**
     * Инструменты профессий (с 0.37.0): кирка, серп и топор. Надеваются в слот своей профессии
     * в окне «Ремёсел», в бою не участвуют и в лист героя не входят - их модификаторы работают
     * только в своей профессии.
     */
    TOOL_MINING,
    TOOL_HERBALISM,
    TOOL_WOODCUTTING,
    TOOL_SMITHING,
    TOOL_ALCHEMY,
    TOOL_CARTOGRAPHY,
    /** Инструмент зачарователя (0.66.0): его свитки ставят зачарования на снаряжение. */
    TOOL_ENCHANTING;

    val isTool: Boolean get() = name.startsWith("TOOL_")
}