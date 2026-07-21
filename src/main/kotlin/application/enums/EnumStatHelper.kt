package application.enums

enum class EnumStatHelper(val nameRu: String, val description: String, val type: EnumStatType, val step: Byte) {
    // ========== STOCK - Основные характеристики ==========
    STOCK_HEALTH("Здоровье", "", EnumStatType.STOCK, 10),
    STOCK_MANA("Мана", "", EnumStatType.STOCK, 5),
    STOCK_ENERGY("Энергия", "", EnumStatType.STOCK, 5),
    STOCK_STRENGTH("Сила", "", EnumStatType.STOCK, 2),
    STOCK_AGILITY("Ловкость", "", EnumStatType.STOCK, 2),
    STOCK_INTELLECT("Интеллект", "", EnumStatType.STOCK, 2),
    STOCK_CONSTITUTION("Телосложение", "", EnumStatType.STOCK, 2),

    // ========== STOCK - Боевые характеристики ==========
    STOCK_ATTACK_PHYSICAL("Физический урон", "Базовый физический урон", EnumStatType.STOCK, 15),
    STOCK_ATTACK_MAGICAL("Магический урон", "Базовый магический урон", EnumStatType.STOCK, 10),
    STOCK_ATTACK_FIRE("Огненный урон", "Дополнительный урон огнём", EnumStatType.STOCK, 8),
    STOCK_ATTACK_COLD("Ледяной урон", "Дополнительный урон льдом", EnumStatType.STOCK, 8),
    STOCK_ATTACK_LIGHTNING("Электрический урон", "Дополнительный урон электричеством", EnumStatType.STOCK, 8),
    STOCK_ATTACK_CHAOS("Хаотический урон", "Игнорирует щиты", EnumStatType.STOCK, 5),

    // ========== STOCK - Защита ==========
    STOCK_ARMOR("Броня", "Уменьшает физический урон", EnumStatType.STOCK, 10),
    STOCK_EVASION("Уклонение", "Шанс уклониться от атаки", EnumStatType.STOCK, 8),
    STOCK_ENERGY_SHIELD("Энергетический щит", "Поглощает урон до истощения", EnumStatType.STOCK, 7),
    STOCK_BLOCK_CHANCE("Шанс блока", "Шанс заблокировать атаку щитом", EnumStatType.STOCK, 4),

    // ========== STOCK - Критические характеристики ==========
    STOCK_CRITICAL_CHANCE("Шанс крита", "Вероятность критического удара", EnumStatType.STOCK, 2),
    STOCK_CRITICAL_DAMAGE("Критический урон", "Множитель урона при крите", EnumStatType.STOCK, 10),
    STOCK_CRITICAL_MULTIPLIER("Множитель крита", "Дополнительный % к крит. урону", EnumStatType.STOCK, 15),

    // ========== STOCK - Скорость ==========
    STOCK_ATTACK_SPEED("Скорость атаки", "Атак в секунду", EnumStatType.STOCK, 5),
    STOCK_CAST_SPEED("Скорость заклинаний", "Заклинаний в секунду", EnumStatType.STOCK, 3),
    STOCK_MOVEMENT_SPEED("Скорость передвижения", "Юнитов в секунду", EnumStatType.STOCK, 4),

    // ========== STOCK - Сопротивления ==========
    STOCK_RESIST_FIRE("Сопротивление огню", "% сопротивления огненному урону", EnumStatType.STOCK, 5),
    STOCK_RESIST_COLD("Сопротивление льду", "% сопротивления ледяному урону", EnumStatType.STOCK, 5),
    STOCK_RESIST_LIGHTNING("Сопротивление электричеству", "% сопротивления электричеству", EnumStatType.STOCK, 5),
    STOCK_RESIST_CHAOS("Сопротивление хаосу", "% сопротивления хаотическому урону", EnumStatType.STOCK, 3),
    STOCK_RESIST_ALL("Все сопротивления", "Базовое сопротивление всем стихиям", EnumStatType.STOCK, 4),

    // ========== STOCK - Регенерация ==========
    STOCK_HEALTH_REGEN("Регенерация здоровья", "Здоровья в секунду", EnumStatType.STOCK, 2),
    STOCK_MANA_REGEN("Регенерация маны", "Маны в секунду", EnumStatType.STOCK, 1),
    STOCK_ENERGY_REGEN("Регенерация энергии", "Энергии в секунду", EnumStatType.STOCK, 1),
    STOCK_LEECH_PHYSICAL("Вампиризм физ.", "% физического урона в здоровье", EnumStatType.STOCK, 3),
    STOCK_LEECH_MAGICAL("Вампиризм маг.", "% магического урона в здоровье", EnumStatType.STOCK, 3),
    STOCK_LEECH_ALL("Вампиризм общ.", "% всего урона в здоровье", EnumStatType.STOCK, 2),

    // ========== STOCK - Инвентарь и ресурсы ==========
    STOCK_INVENTORY_SIZE("Размер инвентаря", "Количество слотов", EnumStatType.STOCK, 1),
    STOCK_GOLD("Золото", "Текущее золото", EnumStatType.STOCK, 1),
    STOCK_EXPERIENCE("Опыт", "Текущий опыт", EnumStatType.STOCK, 1),

    // ========== STOCK - Особые статы ==========
    STOCK_RARITY("Редкость находок", "Шанс найти редкие предметы", EnumStatType.STOCK, 2),
    STOCK_QUANTITY("Количество находок", "Больше предметов с монстров", EnumStatType.STOCK, 2),
    STOCK_STUN_THRESHOLD("Порог оглушения", "Сложнее оглушить", EnumStatType.STOCK, 3),
    STOCK_AURA_EFFECT("Эффективность аур", "Увеличение силы аур", EnumStatType.STOCK, 4),
    STOCK_CURSE_EFFECT("Эффективность проклятий", "Увеличение силы проклятий", EnumStatType.STOCK, 3),
    STOCK_CAST_STRENGTH("Сила заклинаний", "Увеличение силы заклинаний", EnumStatType.STOCK, 5),

    // ========== BOOL - Состояния персонажа ==========
    BOOL_ALIVE("Живой", "Персонаж жив", EnumStatType.BOOL, 0),
    BOOL_BANNED("Заблокирован", "Персонаж заблокирован", EnumStatType.BOOL, 0),
    BOOL_IN_COMBAT("В бою", "Находится в режиме боя", EnumStatType.BOOL, 0),
    BOOL_STUNNED("Оглушён", "Не может действовать", EnumStatType.BOOL, 0),
    BOOL_FROZEN("Заморожен", "Не может двигаться или атаковать", EnumStatType.BOOL, 0),
    BOOL_BURNING("Горит", "Получает периодический урон", EnumStatType.BOOL, 0),
    BOOL_POISONED("Отравлен", "Получает урон ядом", EnumStatType.BOOL, 0),
    BOOL_BLEEDING("Кровотечение", "Получает урон при движении", EnumStatType.BOOL, 0),
    BOOL_SHOCKED("Шок", "Получает увеличенный урон", EnumStatType.BOOL, 0),
    BOOL_CHILLED("Охлаждён", "Замедлен", EnumStatType.BOOL, 0),
    BOOL_CURSED("Проклят", "Под действием проклятия", EnumStatType.BOOL, 0),
    BOOL_INVISIBLE("Невидим", "Невидим для врагов", EnumStatType.BOOL, 0),
    BOOL_INVINCIBLE("Неуязвим", "Не получает урон", EnumStatType.BOOL, 0),
    BOOL_CONCENTRATING("Концентрация", "Кастует заклинание", EnumStatType.BOOL, 0),
}