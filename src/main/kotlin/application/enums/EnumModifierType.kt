package application.enums

/**
 * Тип модификатора — определяет как он складывается с другими.
 *
 * Формула итогового значения характеристики (PoE-стиль):
 *   final = (base + Σflat) × (1 + (Σincreased − Σreduced) / 100) × Π(1 + more/100) × Π(1 − less/100)
 *
 * FLAT_ADD и INCREASED — самые частые типы.
 * MORE / LESS — мощнее, встречаются на уникальных предметах и мощных аффиксах.
 */
enum class EnumModifierType(val code: Int, val displayRu: String) {

    /** +X — прибавляется напрямую к базовому значению */
    FLAT_ADD(0, "плоский бонус"),

    /** X% increased — суммируется со всеми другими increased, затем множится на базу+flat */
    INCREASED(1, "увеличение"),

    /** X% more — каждый перемножается независимо (сильнее increased) */
    MORE(2, "больше"),

    /** X% less — каждый делит независимо (отрицательный multiple) */
    LESS(3, "меньше"),

    /** X% reduced — отнимается от суммы increased (зеркало INCREASED) */
    REDUCED(4, "уменьшение"),

    /** X% penetration — игнорирует X% сопротивления цели */
    PENETRATION(5, "проникновение"),

    /** X% leech — восстанавливает X% от нанесённого урона как здоровье/ману */
    LEECH(6, "вампиризм"),

    /** X% extra as element — добавляет X% урона как другой тип */
    EXTRA_AS(7, "дополнительно как");

    companion object {
        fun byCode(code: Int) = entries.first { it.code == code }
    }
}
