package application.enums

enum class EnumModifierSource {

    /**
     * Встроенный модификатор базового предмета.
     */
    IMPLICIT,

    /**
     * Префикс редкого предмета.
     */
    PREFIX,

    /**
     * Суффикс редкого предмета.
     */
    SUFFIX,

    /**
     * Уникальный модификатор.
     */
    UNIQUE,

    /**
     * Модификация через enchant.
     */
    ENCHANTMENT,

    /**
     * Особая модификация предмета.
     */
    CORRUPTION
}