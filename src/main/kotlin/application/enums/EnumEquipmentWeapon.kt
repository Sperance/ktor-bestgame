package application.enums

enum class EnumEquipmentWeapon(val text: String, val twoHanded: Boolean) {
    SWORD("Sword", false),
    LONGSWORD("Longsword", true),
    BOW("Bow", false),
    WAND("Wand", false),
    AXE("Axe", false),
    DOUBLEAXE("Double Axe", true),
    DOUBLESWORD("Double Sword", true),
    MACE("Mace", false),
    TWO_HAND_MACE("Two Hand Mace", true),
    SCEPTRE("Sceptre", false),
    STAFF("Staff", true),
    WARSTAFF("Warstaff", true),
    CLAW("Claw", false),
    DAGGER("Dagger", false),
    RUNE_DAGGER("Rune Dagger", false),
    THRUSTING_SWORD("Thrusting One Hand Sword", false),
    BLADE("Blade", false),
}