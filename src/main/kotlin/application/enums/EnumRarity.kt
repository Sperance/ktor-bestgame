package application.enums

enum class EnumRarity(
    /**
     * Количество префиксов, которое роллится на предмете этой редкости.
     */
    val prefixCount: Int,

    /**
     * Количество суффиксов, которое роллится на предмете этой редкости.
     */
    val suffixCount: Int
) {
    COMMON(1, 1),
    UNCOMMON(1, 1),
    RARE(1, 1),
    EPIC(2, 1),
    UNIQUE(0, 0),
    MYTHICAL(3, 3);
}
