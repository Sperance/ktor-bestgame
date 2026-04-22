package application.enums

enum class EnumStatHelper(val nameRu: String, val description: String, val type: EnumStatType) {
    // ========== STOCK - Основные характеристики ==========
    STOCK_HEALTH("Здоровье", "", EnumStatType.STOCK),
    STOCK_MANA("Мана", "", EnumStatType.STOCK),
    STOCK_ENERGY("Энергия", "", EnumStatType.STOCK),
    STOCK_STRENGTH("Сила", "", EnumStatType.STOCK),
    STOCK_AGILITY("Ловкость", "", EnumStatType.STOCK),
    STOCK_INTELLECT("Интеллект", "", EnumStatType.STOCK),
    STOCK_CONSTITUTION("Телосложение", "", EnumStatType.STOCK),

    // ========== STOCK - Боевые характеристики ==========
    STOCK_ATTACK_PHYSICAL("Физический урон", "Базовый физический урон", EnumStatType.STOCK),
    STOCK_ATTACK_MAGICAL("Магический урон", "Базовый магический урон", EnumStatType.STOCK),
    STOCK_ATTACK_FIRE("Огненный урон", "Дополнительный урон огнём", EnumStatType.STOCK),
    STOCK_ATTACK_COLD("Ледяной урон", "Дополнительный урон льдом", EnumStatType.STOCK),
    STOCK_ATTACK_LIGHTNING("Электрический урон", "Дополнительный урон электричеством", EnumStatType.STOCK),
    STOCK_ATTACK_CHAOS("Хаотический урон", "Игнорирует щиты", EnumStatType.STOCK),

    // ========== STOCK - Защита ==========
    STOCK_ARMOR("Броня", "Уменьшает физический урон", EnumStatType.STOCK),
    STOCK_EVASION("Уклонение", "Шанс уклониться от атаки", EnumStatType.STOCK),
    STOCK_ENERGY_SHIELD("Энергетический щит", "Поглощает урон до истощения", EnumStatType.STOCK),
    STOCK_BLOCK_CHANCE("Шанс блока", "Шанс заблокировать атаку щитом", EnumStatType.STOCK),

    // ========== STOCK - Критические характеристики ==========
    STOCK_CRITICAL_CHANCE("Шанс крита", "Вероятность критического удара", EnumStatType.STOCK),
    STOCK_CRITICAL_DAMAGE("Критический урон", "Множитель урона при крите", EnumStatType.STOCK),
    STOCK_CRITICAL_MULTIPLIER("Множитель крита", "Дополнительный % к крит. урону", EnumStatType.STOCK),

    // ========== STOCK - Скорость ==========
    STOCK_ATTACK_SPEED("Скорость атаки", "Атак в секунду", EnumStatType.STOCK),
    STOCK_CAST_SPEED("Скорость заклинаний", "Заклинаний в секунду", EnumStatType.STOCK),
    STOCK_MOVEMENT_SPEED("Скорость передвижения", "Юнитов в секунду", EnumStatType.STOCK),

    // ========== STOCK - Сопротивления ==========
    STOCK_RESIST_FIRE("Сопротивление огню", "% сопротивления огненному урону", EnumStatType.STOCK),
    STOCK_RESIST_COLD("Сопротивление льду", "% сопротивления ледяному урону", EnumStatType.STOCK),
    STOCK_RESIST_LIGHTNING("Сопротивление электричеству", "% сопротивления электричеству", EnumStatType.STOCK),
    STOCK_RESIST_CHAOS("Сопротивление хаосу", "% сопротивления хаотическому урону", EnumStatType.STOCK),
    STOCK_RESIST_ALL("Все сопротивления", "Базовое сопротивление всем стихиям", EnumStatType.STOCK),

    // ========== STOCK - Регенерация ==========
    STOCK_HEALTH_REGEN("Регенерация здоровья", "Здоровья в секунду", EnumStatType.STOCK),
    STOCK_MANA_REGEN("Регенерация маны", "Маны в секунду", EnumStatType.STOCK),
    STOCK_ENERGY_REGEN("Регенерация энергии", "Энергии в секунду", EnumStatType.STOCK),
    STOCK_LEECH_PHYSICAL("Вампиризм физ.", "% физического урона в здоровье", EnumStatType.STOCK),
    STOCK_LEECH_MAGICAL("Вампиризм маг.", "% магического урона в здоровье", EnumStatType.STOCK),
    STOCK_LEECH_ALL("Вампиризм общ.", "% всего урона в здоровье", EnumStatType.STOCK),

    // ========== STOCK - Инвентарь и ресурсы ==========
    STOCK_INVENTORY_SIZE("Размер инвентаря", "Количество слотов", EnumStatType.STOCK),
    STOCK_GOLD("Золото", "Текущее золото", EnumStatType.STOCK),
    STOCK_EXPERIENCE("Опыт", "Текущий опыт", EnumStatType.STOCK),

    // ========== STOCK - Особые статы ==========
    STOCK_RARITY("Редкость находок", "Шанс найти редкие предметы", EnumStatType.STOCK),
    STOCK_QUANTITY("Количество находок", "Больше предметов с монстров", EnumStatType.STOCK),
    STOCK_FLASK_CHARGE_GAIN("Заряды флаконов", "Скорость восстановления зарядов", EnumStatType.STOCK),
    STOCK_STUN_THRESHOLD("Порог оглушения", "Сложнее оглушить", EnumStatType.STOCK),
    STOCK_AURA_EFFECT("Эффективность аур", "Увеличение силы аур", EnumStatType.STOCK),
    STOCK_CURSE_EFFECT("Эффективность проклятий", "Увеличение силы проклятий", EnumStatType.STOCK),
    STOCK_CAST_STRENGTH("Сила заклинаний", "Увеличение силы заклинаний", EnumStatType.STOCK),

    // ========== BOOL - Состояния персонажа ==========
    BOOL_ALIVE("Живой", "Персонаж жив", EnumStatType.BOOL),
    BOOL_BANNED("Заблокирован", "Персонаж заблокирован", EnumStatType.BOOL),
    BOOL_IN_COMBAT("В бою", "Находится в режиме боя", EnumStatType.BOOL),
    BOOL_STUNNED("Оглушён", "Не может действовать", EnumStatType.BOOL),
    BOOL_FROZEN("Заморожен", "Не может двигаться или атаковать", EnumStatType.BOOL),
    BOOL_BURNING("Горит", "Получает периодический урон", EnumStatType.BOOL),
    BOOL_POISONED("Отравлен", "Получает урон ядом", EnumStatType.BOOL),
    BOOL_BLEEDING("Кровотечение", "Получает урон при движении", EnumStatType.BOOL),
    BOOL_SHOCKED("Шок", "Получает увеличенный урон", EnumStatType.BOOL),
    BOOL_CHILLED("Охлаждён", "Замедлен", EnumStatType.BOOL),
    BOOL_CURSED("Проклят", "Под действием проклятия", EnumStatType.BOOL),
    BOOL_INVISIBLE("Невидим", "Невидим для врагов", EnumStatType.BOOL),
    BOOL_INVINCIBLE("Неуязвим", "Не получает урон", EnumStatType.BOOL),
    BOOL_CONCENTRATING("Концентрация", "Кастует заклинание", EnumStatType.BOOL),

    // ========== HISTORY - Достижения ==========
    HISTORY_KILLS("Убийств всего", "Всего убито монстров", EnumStatType.HISTORY),
    HISTORY_CRITICAL_HITS("Критических попаданий", "Всего критических ударов", EnumStatType.HISTORY),
    HISTORY_GOLD_GAINED("Золота получено", "Всего золота за игру", EnumStatType.HISTORY),
    HISTORY_DEATHS("Смертей", "Количество смертей", EnumStatType.HISTORY),

    // ========== HISTORY - Боевая статистика ==========
    HISTORY_DAMAGE_DEALT("Урона нанесено", "Общий нанесённый урон", EnumStatType.HISTORY),
    HISTORY_DAMAGE_TAKEN("Урона получено", "Общий полученный урон", EnumStatType.HISTORY),
    HISTORY_HEALING_DONE("Лечения нанесено", "Всего восстановлено здоровья", EnumStatType.HISTORY),
    HISTORY_BOSS_KILLS("Боссов убито", "Количество побеждённых боссов", EnumStatType.HISTORY),
    HISTORY_QUESTS_COMPLETED("Квестов выполнено", "Всего завершённых квестов", EnumStatType.HISTORY),
    HISTORY_CHESTS_OPENED("Сундуков открыто", "Количество открытых сундуков", EnumStatType.HISTORY),
    HISTORY_CRAFTING_ATTEMPTS("Попыток крафта", "Всего попыток улучшения предметов", EnumStatType.HISTORY),
    HISTORY_DUNGEONS_CLEARED("Подземелий зачищено", "Полностью пройденных подземелий", EnumStatType.HISTORY),
    HISTORY_TRAVEL_DISTANCE("Пройдено шагов", "Общая дистанция передвижения", EnumStatType.HISTORY),
    HISTORY_PORTALS_USED("Порталов использовано", "Количество телепортаций", EnumStatType.HISTORY),
    HISTORY_ITEMS_IDENTIFIED("Предметов опознано", "Всего опознанных предметов", EnumStatType.HISTORY),
    HISTORY_VENDOR_TRANSACTIONS("Продано торговцу", "Количество проданных предметов", EnumStatType.HISTORY)
}