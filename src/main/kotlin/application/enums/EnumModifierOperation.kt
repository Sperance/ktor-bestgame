package application.enums

enum class EnumModifierOperation {

    /**
     * Простое сложение.
     *
     * Например:
     * +20 Strength
     */
    ADD,

    /**
     * Процентное увеличение.
     *
     * Например:
     * 20% increased Armor
     *
     * Все INCREASED одного типа складываются между собой.
     */
    INCREASED,

    /**
     * Мультипликативный бонус.
     *
     * Например:
     * 20% more Damage
     */
    MORE,

    /**
     * Полностью заменяет рассчитанное значение.
     */
    SET
}