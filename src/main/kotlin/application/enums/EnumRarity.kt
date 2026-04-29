package application.enums

/**
 * Редкость предмета — определяет количество явных аффиксов и имплицитов.
 *
 * Явные аффиксы: PREFIX + SUFFIX (PoE-стиль).
 * LEGENDARY имеет фиксированные уникальные моды, не случайные.
 *
 * Примеры:
 *   COMMON:    0 явных, 1 implicit → только базовые свойства слота
 *   UNCOMMON:  1 prefix + 1 suffix → "Кровавый Шлем Проворства"
 *   RARE:      2-3 prefix + 2-3 suffix → редкий предмет с именем
 *   EPIC:      3-4 prefix + 3-4 suffix → мощный предмет
 *   LEGENDARY: 4-6 prefix + 4-6 suffix → уникальный предмет, особые моды
 */
enum class EnumRarity(
    val text: String,
    val color: String,
    val maxPrefixes: Int,
    val maxSuffixes: Int,
    val maxImplicits: Int,
    val minExplicit: Int
) {
    COMMON("Обычный", "#9d9d9d", 0, 0, 1, 0),
    UNCOMMON("Необычный", "#1eff00", 1, 1, 1, 2),
    RARE("Редкий", "#0070dd", 3, 3, 1, 4),
    EPIC("Эпический", "#a335ee", 4, 4, 2, 6),
    LEGENDARY("Легендарный", "#ff8000", 6, 6, 3, 8)
}
