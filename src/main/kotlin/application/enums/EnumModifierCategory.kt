package application.enums

/**
 * Категория модификатора — откуда он появился на предмете.
 *
 * Явные (isExplicit=true) — можно перероллить крафтом.
 * Неявные (isExplicit=false) — фиксированы базой предмета.
 */
enum class EnumModifierCategory(val code: Int, val nameRu: String, val isExplicit: Boolean) {

    /** Скрытое свойство, врождённое для типа предмета */
    IMPLICIT(0, "Скрытое", false),

    /** Явный аффикс — префикс (в названии перед базой) */
    PREFIX(1, "Префикс", true),

    /** Явный аффикс — суффикс (в названии после базы) */
    SUFFIX(2, "Суффикс", true),

    /** Крафтовый мод — добавляется через крафт-мастера */
    CRAFTED(3, "Крафтовое", true),

    /** Зачарование — из лабиринта */
    ENCHANT(4, "Зачарование", false),

    /** Испорченное — из Вааль-орба */
    CORRUPTED(5, "Испорченное", false),

    /** Уникальный эффект — только на уникальных предметах */
    UNIQUE(6, "Уникальное", false);

    companion object {
        fun byCode(code: Int) = entries.first { it.code == code }
    }
}
