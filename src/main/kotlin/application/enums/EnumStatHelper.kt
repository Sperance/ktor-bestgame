package application.enums

import kotlinx.serialization.Serializable
import server.serializers.IntEnumStatSerializer

/**
 * Характеристика, которую умеет считать калькулятор модификаторов.
 *
 * [order] задаёт глобальный порядок вычисления и вместе с ним - направление
 * конверсий: модификатор вида "X за каждые Y" разрешён только из стата
 * с меньшим order в стат с большим. Благодаря этому цикл вида
 * "Сила за Интеллект" плюс "Интеллект за Силу" невыразим в принципе,
 * а расчёт сводится к одному проходу по статам в порядке возрастания order.
 *
 * Номера заданы явно и с запасом между группами: добавление стата в середину
 * не должно требовать перенумерации соседей, а перестановка строк в файле
 * не должна молча менять правила игры.
 */
@Serializable(with = IntEnumStatSerializer::class)
interface IntEnumStat {
    val order: Int
}

enum class EnumStatStock(override val order: Int) : IntEnumStat {
    // ========== Атрибуты: источник конверсий, считаются первыми ==========
    STOCK_STRENGTH(10),
    STOCK_AGILITY(11),
    STOCK_INTELLECT(12),
    STOCK_CONSTITUTION(13),

    // ========== Запас характеристик ==========
    STOCK_HEALTH(100),
    STOCK_MANA(101),
    STOCK_ENERGY(102),
    STOCK_ENERGY_SHIELD(103),

    // ========== Защита ==========
    STOCK_ARMOR(120),
    STOCK_EVASION(121),
    STOCK_BLOCK_CHANCE(122),
    STOCK_STUN_THRESHOLD(123),
    /** Шанс заблокировать заклинание, в процентах (с 0.36.0): сверх доли обычного блока. */
    STOCK_SPELL_BLOCK(124),
    /** Дополнительное снижение физического урона после брони, в процентах. */
    STOCK_PHYSICAL_REDUCTION(125),
    /** Шанс избежать оглушения, в процентах. */
    STOCK_AVOID_STUN(126),

    // ========== Сопротивления ==========
    STOCK_RESIST_FIRE(140),
    STOCK_RESIST_COLD(141),
    STOCK_RESIST_LIGHTNING(142),
    STOCK_RESIST_CHAOS(143),
    STOCK_RESIST_ALL(144),
    /** Прибавка к потолку сопротивления (с 0.36.0); «все» - к трём стихиям. */
    STOCK_RESIST_MAX_FIRE(145),
    STOCK_RESIST_MAX_COLD(146),
    STOCK_RESIST_MAX_LIGHTNING(147),
    STOCK_RESIST_MAX_CHAOS(148),
    STOCK_RESIST_MAX_ALL(149),

    // ========== Урон ==========
    STOCK_ATTACK_PHYSICAL(200),
    STOCK_ATTACK_MAGICAL(201),
    STOCK_ATTACK_FIRE(202),
    STOCK_ATTACK_COLD(203),
    STOCK_ATTACK_LIGHTNING(204),
    STOCK_ATTACK_CHAOS(205),

    // ========== Критические характеристики ==========
    STOCK_CRITICAL_CHANCE(220),
    STOCK_CRITICAL_DAMAGE(221),
    STOCK_CRITICAL_MULTIPLIER(222),
    STOCK_CRITICAL_VAMPIRE(223),

    // ========== Скорость ==========
    STOCK_ATTACK_SPEED(240),
    STOCK_CAST_SPEED(241),
    STOCK_MOVEMENT_SPEED(242),

    // ========== Регенерация и вампиризм ==========
    STOCK_HEALTH_REGEN(260),
    STOCK_MANA_REGEN(261),
    STOCK_ENERGY_REGEN(262),
    STOCK_LEECH_PHYSICAL(263),
    STOCK_LEECH_MAGICAL(264),
    STOCK_LEECH_ALL(265),
    /** Здоровье и мана за убийство и за каждый удар атакой (с 0.36.0). */
    STOCK_HEALTH_ON_KILL(266),
    STOCK_MANA_ON_KILL(267),
    STOCK_HEALTH_ON_HIT(268),
    STOCK_MANA_ON_HIT(269),
    /** Лишние заряды флакона на забег и увеличение его лечения в процентах. */
    STOCK_FLASK_CHARGES(270),
    STOCK_FLASK_RECOVERY(271),

    // ========== Состояния (с 0.36.0): все - проценты, база героя в правилах боя ==========
    /** Шанс наложить состояние ударом с уроном его типа, сверх базы правила. */
    STOCK_IGNITE_CHANCE(280),
    STOCK_FREEZE_CHANCE(281),
    STOCK_SHOCK_CHANCE(282),
    STOCK_POISON_CHANCE(283),
    STOCK_BLEED_CHANCE(284),
    /** Увеличение урона со временем от поджога, яда и кровотечения. */
    STOCK_BURNING_DAMAGE(285),
    STOCK_POISON_DAMAGE(286),
    STOCK_BLEED_DAMAGE(287),
    /** Шанс избежать состояния. */
    STOCK_AVOID_IGNITE(288),
    STOCK_AVOID_CHILL(289),
    STOCK_AVOID_FREEZE(290),
    STOCK_AVOID_SHOCK(291),
    STOCK_AVOID_POISON(292),
    STOCK_AVOID_BLEED(293),
    /** Сокращение длительности состояния на себе. */
    STOCK_IGNITE_DURATION_ON_SELF(294),
    STOCK_CHILL_DURATION_ON_SELF(295),
    STOCK_FREEZE_DURATION_ON_SELF(296),
    STOCK_SHOCK_DURATION_ON_SELF(297),
    STOCK_POISON_DURATION_ON_SELF(298),
    STOCK_BLEED_DURATION_ON_SELF(299),

    // ========== Инвентарь и ресурсы ==========
    STOCK_INVENTORY_SIZE(300),
    STOCK_GOLD(301),
    STOCK_EXPERIENCE(302),

    // ========== Особые статы ==========
    STOCK_RARITY(320),
    STOCK_QUANTITY(321),
    STOCK_CAST_STRENGTH(324),
    /** Радиус света на карте кампании в клетках (с 0.30.0): сколько вокруг героя видно сквозь туман. */
    STOCK_LIGHT_RADIUS(325),
    /** Больше сундуков на карте кампании, в процентах (с 0.31.0): каждые полные 100% - ещё один, остаток - шанс. */
    STOCK_CHEST_QUANTITY(326),

    // ========== Карты (с 0.35.0): свойства предмета-карты, а не героя - на лист персонажа не влияют ==========
    MAP_MONSTER_LIFE(400),
    MAP_MONSTER_DAMAGE(401),
    MAP_MONSTER_SPEED(402),
    MAP_MONSTER_RESIST(403),
    MAP_PACK_SIZE(404),
    MAP_MONSTER_RARITY(405),
    MAP_HERO_LIGHT(406),
    MAP_HERO_FLASK(407),
    MAP_HERO_RESIST(408),
    MAP_HERO_REGEN(409),
    MAP_CHESTS(410),
    MAP_QUANTITY(411),
    MAP_RARITY(412),
    MAP_EXPERIENCE(413),
    // Алхимия (0.38.0): магические и редкие монстры карты по отдельности.
    MAP_MAGIC_MONSTERS(414),
    MAP_RARE_MONSTERS(415),

    // ========== Ремёсла (0.37.0): инструменты и ветка дерева «Ремесло» ==========
    // Скорость цикла, процент лишней единицы (каждые 100 - гарантированная), снижение шанса
    // «ничего», опыт профессии и шанс побочной находки. Инструмент считается только в своей
    // профессии, поэтому в лист героя инструменты не входят - там только дерево.
    STOCK_WORK_SPEED(420),
    STOCK_WORK_YIELD(421),
    STOCK_WORK_LUCK(422),
    STOCK_WORK_EXPERIENCE(423),
    STOCK_WORK_FIND(424),
}

enum class EnumStatBool(override val order: Int) : IntEnumStat {
    BOOL_ALIVE(1000),
    BOOL_BANNED(1001),
    BOOL_IN_COMBAT(1002),
    BOOL_STUNNED(1003),
    BOOL_FROZEN(1004),
    BOOL_BURNING(1005),
    BOOL_POISONED(1006),
    BOOL_BLEEDING(1007),
    BOOL_SHOCKED(1008),
    BOOL_CHILLED(1009),
    BOOL_INVISIBLE(1011),
    BOOL_INVINCIBLE(1012),
    BOOL_CONCENTRATING(1013),
}

enum class EnumStatProfession(override val order: Int) : IntEnumStat {
    PROFESSION_LUMBERJACK(2000),
    PROFESSION_STONEMASON(2001),
    PROFESSION_ALCHEMY(2002),
    PROFESSION_BLACKSMITH(2003),
    PROFESSION_BUILDER(2004),
    PROFESSION_TAILOR(2005),
    PROFESSION_ENCHANTER(2006),
    PROFESSION_JEWELER(2007),
    PROFESSION_COOK(2008),
    PROFESSION_FISHERMAN(2009),
    PROFESSION_MINER(2010),
    PROFESSION_FARMER(2011),
    PROFESSION_HERBALIST(2012),
    PROFESSION_CARPENTER(2013),
    PROFESSION_CARTOGRAPHER(2014),
    PROFESSION_HUNTER(2015),
}

enum class EnumStatBattle(override val order: Int) : IntEnumStat {
    BATTLE_MELEE_COMBAT(3000),
    BATTLE_SWORDSMANSHIP(3001),
    BATTLE_AXE_COMBAT(3002),
    BATTLE_POLEARM_COMBAT(3003),
    BATTLE_UNARMED_COMBAT(3004),
    BATTLE_DUAL_WIELDING(3005),
    BATTLE_TWO_HANDED_WEAPON(3006),
    BATTLE_RANGE_COMBAT(3007),
    BATTLE_ARCHERY(3008),
    BATTLE_CROSSBOW(3009),
    BATTLE_THROWING(3010),
    BATTLE_MAGIC_COMBAT(3011),
    BATTLE_FIRE_MAGIC(3012),
    BATTLE_WATER_MAGIC(3013),
    BATTLE_EARTH_MAGIC(3014),
    BATTLE_AIR_MAGIC(3015),
    BATTLE_ELECTRIC_MAGIC(3016),
    BATTLE_DARK_MAGIC(3017),
    BATTLE_LIGHT_MAGIC(3018),
    BATTLE_CHAOS_MAGIC(3019),
    BATTLE_SUMMONING(3020),
}
