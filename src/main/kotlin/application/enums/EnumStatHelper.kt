package application.enums

enum class EnumStatHelper(val code: String, val nameRu: String, val description: String, val type: EnumStatType) {
    STOCK_HEALTH("S_HEALTH", "Здоровье", "", EnumStatType.STOCK),
    STOCK_MANA("S_MANA", "Мана", "", EnumStatType.STOCK),
    STOCK_ENERGY("S_ENERGY", "Энергия", "", EnumStatType.STOCK),
    STOCK_STRENGTH("S_STR", "Сила", "", EnumStatType.STOCK),
    STOCK_AGILITY("S_AGI", "Ловкость", "", EnumStatType.STOCK),
    STOCK_INTELLECT("S_INT", "Интеллект", "", EnumStatType.STOCK),
    STOCK_INVENTORY_SIZE("S_INV", "Размер инвентаря", "", EnumStatType.STOCK),
    STOCK_CRITICAL_CHANCE("S_CRIT_CH", "Шанс критического удара", "", EnumStatType.STOCK),
    STOCK_CRITICAL_DAMAGE("S_CRIT_DMG", "Урон критического удара", "", EnumStatType.STOCK),
    STOCK_ATTACK_SPEED("S_SPD", "Скорость атаки", "", EnumStatType.STOCK),
    STOCK_ARMOR("S_ARM", "Броня", "", EnumStatType.STOCK),
    STOCK_ATTACK_PHYSICAL("S_FATK", "Физический урон", "", EnumStatType.STOCK),
    STOCK_ATTACK_MAGICAL("S_MATK", "Магический урон", "", EnumStatType.STOCK),

    BOOL_ALIVE("B_ALIVE", "Живой", "", EnumStatType.BOOL),
    BOOL_BANNED("B_BANNED", "Заблокирован", "", EnumStatType.BOOL),

    HISTORY_KILLS("H_KILLS", "Убийств всего", "", EnumStatType.HISTORY),
    HISTORY_CRITICAL_HITS("H_CRITS", "Критических попаданий", "", EnumStatType.HISTORY),
    HISTORY_GOLD_GAINED("H_GOLD_GAINED", "Золота получено", "", EnumStatType.HISTORY),
}