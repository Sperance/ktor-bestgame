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
}