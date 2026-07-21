package application.enums

enum class EnumEquipmentWeapon(val text: String, val twoHanded: Boolean) {
    SWORD("Sword", false),
    LONGSWORD("Longsword", true),
    BOW("Bow", false),
    WAND("Wand", false),
    AXE("Axe", false),
    DOUBLEAXE("Double Axe", true),
    DOUBLESWORD("Double Sword", true),
    BLADE("Blade", false),
}