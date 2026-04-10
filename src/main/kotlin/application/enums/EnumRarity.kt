package application.enums

enum class EnumRarity(val text: String, val color: String) {
    COMMON("Обычный", "#9d9d9d"),
    UNCOMMON("Необычный", "#1eff00"),
    RARE("Редкий", "#0070dd"),
    EPIC("Эпический", "#a335ee"),
    LEGENDARY("Легендарный", "#ff8000")
}
