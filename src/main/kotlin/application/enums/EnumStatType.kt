package application.enums

enum class EnumStatType(val code: Int) {

    /**
     * Основная характеристика для всей игры
     */
    STOCK(0),

    /**
     * Булевое значение для характеристки
     */
    BOOL(1),

    /**
     * Характеристика для статистики по персонажу
     */
    HISTORY(2),
}