package application.enums

/**
 * Счётчики — статистики, которые только увеличиваются.
 * Код используется для компактной JSONB-сериализации: "K1:150"
 */
enum class EnumCounter(val code: String, val text: String) {
    // Боевые
    KILLS("K1", "Убийств"),
    DEATHS("K2", "Смертей"),
    DAMAGE_DEALT("K3", "Урон нанесённый"),
    DAMAGE_TAKEN("K4", "Урон полученный"),
    CRITICAL_HITS("K5", "Критических ударов"),
    HITS_DEALT("K6", "Ударов нанесено"),
    HITS_TAKEN("K7", "Ударов получено"),

    // Экономика
    GOLD_EARNED("E1", "Золота заработано"),
    GOLD_SPENT("E2", "Золота потрачено"),
    ITEMS_LOOTED("E3", "Предметов подобрано"),
    ITEMS_SOLD("E4", "Предметов продано"),
    ITEMS_CRAFTED("E5", "Предметов создано"),

    // Прогресс
    QUESTS_COMPLETED("P1", "Квестов выполнено"),
    DUNGEONS_CLEARED("P2", "Подземелий пройдено"),
    BOSSES_KILLED("P3", "Боссов убито"),

    // Ресурсы
    POTIONS_USED("R1", "Зелий использовано"),
    EQUIPMENT_ENHANCED("R2", "Улучшений экипировки"),
}
