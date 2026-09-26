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
    /** Урон всех видов (0.69.0), процент без базы: атаки, чары и умения разом. */
    STOCK_DAMAGE(206),
    /** Урон умений класса (0.69.0), процент без базы. */
    STOCK_SKILL_DAMAGE(207),
    /** Урон чар (0.69.0), процент без базы: ложится на базовый урон чар, а не на удар оружием. */
    STOCK_SPELL_DAMAGE(208),
    /** Урон со временем по цели выше на столько процентов (0.69.0): стат проклятой цели. */
    STOCK_DOT_TAKEN(209),

    // ========== Критические характеристики ==========
    STOCK_CRITICAL_CHANCE(220),
    STOCK_CRITICAL_DAMAGE(221),
    STOCK_CRITICAL_MULTIPLIER(222),
    STOCK_CRITICAL_VAMPIRE(223),
    /** Множитель крита по цели выше на столько пунктов (0.69.0): стат проклятой цели. */
    STOCK_CRITICAL_TAKEN(224),

    // ========== Пробивание и урон по состояниям (0.66.0): проценты, базы нет ==========
    /** Сколько процентов сопротивления цели удар не замечает; «стихии» - все три сразу. */
    STOCK_PENETRATE_FIRE(226),
    STOCK_PENETRATE_COLD(227),
    STOCK_PENETRATE_LIGHTNING(228),
    STOCK_PENETRATE_CHAOS(229),
    STOCK_PENETRATE_ELEMENTAL(230),
    /** Увеличение урона по цели под состоянием: любым или названным. */
    STOCK_DAMAGE_VS_AILED(231),
    STOCK_DAMAGE_VS_BURNING(232),
    STOCK_DAMAGE_VS_CHILLED(233),
    STOCK_DAMAGE_VS_SHOCKED(234),
    STOCK_DAMAGE_VS_POISONED(235),
    STOCK_DAMAGE_VS_BLEEDING(236),

    // ========== Скорость ==========
    STOCK_ATTACK_SPEED(240),
    STOCK_CAST_SPEED(241),
    STOCK_MOVEMENT_SPEED(242),
    /** Скорость перезарядки умений класса (0.69.0), процент без базы. */
    STOCK_COOLDOWN_RECOVERY(243),

    // ========== Регенерация и вампиризм ==========
    STOCK_HEALTH_REGEN(260),
    STOCK_MANA_REGEN(261),
    STOCK_ENERGY_REGEN(262),
    STOCK_LEECH_PHYSICAL(263),
    /** Доля урона, что крадётся маной (0.69.0). */
    STOCK_LEECH_MANA(264),
    STOCK_LEECH_ALL(265),
    /** Здоровье за убийство и за каждый удар атакой (с 0.36.0). */
    STOCK_HEALTH_ON_KILL(266),
    /** Мана за убийство и за удар (0.69.0). */
    STOCK_MANA_ON_KILL(267),
    STOCK_HEALTH_ON_HIT(268),
    STOCK_MANA_ON_HIT(269),
    /** Умения дешевле на столько процентов (0.69.0). */
    STOCK_SKILL_COST(270),
    /** Эффективность резерва аур (0.69.0): резерв делится на (1 + значение / 100). */
    STOCK_RESERVATION(271),
    /** Регенерация здоровья в процентах от максимума в секунду (0.69.0). */
    STOCK_LIFE_REGEN_PERCENT(272),

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
    /** Радиус света на карте кампании в клетках (с 0.30.0): сколько вокруг героя видно сквозь туман. */
    STOCK_LIGHT_RADIUS(325),
    /** Больше сундуков на карте кампании, в процентах (с 0.31.0): каждые полные 100% - ещё один, остаток - шанс. */
    STOCK_CHEST_QUANTITY(326),
    /** Провокация (с 0.62.0): пока жив носитель больше нуля, противники обязаны бить его первым - и через ряды. */
    STOCK_TAUNT(327),

    // ========== Состояния на врагах (0.66.0): длительность того, что накладывает носитель, в процентах ==========
    STOCK_AILMENT_DURATION(330),
    STOCK_IGNITE_DURATION(331),
    STOCK_CHILL_DURATION(332),
    STOCK_FREEZE_DURATION(333),
    STOCK_SHOCK_DURATION(334),
    STOCK_POISON_DURATION(335),
    STOCK_BLEED_DURATION(336),

    // ========== Получаемый урон, восстановление и ответный урон (0.66.0) ==========
    /** Изменение получаемого урона в процентах: минус - меньше, плюс - больше; по типам - сверх общего. */
    STOCK_DAMAGE_TAKEN(350),
    STOCK_PHYSICAL_TAKEN(351),
    STOCK_ELEMENTAL_TAKEN(352),
    STOCK_CHAOS_TAKEN(353),
    /** Скорость восстановления здоровья и щита: регенерация, вампиризм, за удар и за убийство. */
    STOCK_RECOVERY_RATE(354),
    /** Скорость восполнения энергетического щита после паузы. */
    STOCK_SHIELD_RECHARGE(355),
    /** Шипы: плоский физический урон каждому, кто ударил носителя. */
    STOCK_THORNS(356),
    /** Отражение: процент полученного урона возвращается ударившему тем же типом. */
    STOCK_REFLECT(357),
    /** Шанс кровотечения по цели выше на столько пунктов (0.69.0): стат проклятой цели. */
    STOCK_BLEED_TAKEN(358),
    /** Шок на цели сильнее на столько процентов (0.69.0): стат проклятой цели. */
    STOCK_SHOCK_TAKEN(359),
    /** Энергощит в столько процентов своего здоровья (0.69.0): монстр эссенции Скорби. */
    STOCK_SHIELD_OF_LIFE(360),
    /** Скорость атаки выше на столько процентов, пока здоровья меньше половины (0.69.0). */
    STOCK_LOW_LIFE_SPEED(361),
    /** Удар сжигает столько процентов маны цели (0.69.0). */
    STOCK_MANA_BURN(362),
    /** Применяет умения других монстров (0.69.0): 1 - да. */
    STOCK_BORROW_SKILLS(363),

    // ========== Ауры монстров (0.66.0): пока жив носитель, герой в бою страдает ==========
    /** Герой теряет столько процентов всех сопротивлений. */
    AURA_RESIST(380),
    /** Герой получает на столько процентов больше урона. */
    AURA_DAMAGE_TAKEN(381),
    /** Герой бьёт на столько процентов медленнее. */
    AURA_SLOW(382),
    /** Герой восстанавливается на столько процентов медленнее. */
    AURA_RECOVERY(383),
    /** Герой наносит на столько процентов меньше урона (0.69.0). */
    AURA_WEAKEN(384),
    /** Шанс крита героя меньше на столько процентов (0.69.0). */
    AURA_CRIT(385),
    /** Умения героя перезаряжаются на столько процентов медленнее (0.69.0). */
    AURA_COOLDOWN(386),

    // ========== Карты (с 0.35.0): свойства предмета-карты, а не героя - на лист персонажа не влияют ==========
    MAP_MONSTER_LIFE(400),
    MAP_MONSTER_DAMAGE(401),
    MAP_MONSTER_SPEED(402),
    MAP_MONSTER_RESIST(403),
    MAP_PACK_SIZE(404),
    MAP_MONSTER_RARITY(405),
    MAP_HERO_LIGHT(406),
    MAP_HERO_RESIST(408),
    MAP_HERO_REGEN(409),
    MAP_CHESTS(410),
    MAP_QUANTITY(411),
    MAP_RARITY(412),
    MAP_EXPERIENCE(413),
    // Алхимия (0.38.0): магические и редкие монстры карты по отдельности.
    MAP_MAGIC_MONSTERS(414),
    MAP_RARE_MONSTERS(415),
    // Герой медленнее на карте (0.65.0): вредная строка, вычитается из скорости передвижения.
    MAP_HERO_SLOW(416),
    // Карты, умения и фляги (0.69.0): риски новых механик.
    /** Фляги героя получают на столько процентов меньше зарядов. */
    MAP_FLASK_CHARGES(407),
    /** Регенерация маны героя меньше на столько процентов. */
    MAP_HERO_MANA_REGEN(417),
    /** Монстры колдуют и перезаряжают умения на столько процентов быстрее. */
    MAP_MONSTER_CAST(418),
    /** Умения героя дороже на столько процентов. */
    MAP_SKILL_COST(419),
    // Карты, продолжение (0.66.0): штрафы герою и усиления монстров как в POE, плюс награды герою.
    /** Герой получает на столько процентов больше урона. */
    MAP_HERO_DAMAGE_TAKEN(460),
    /** Герой восстанавливается на столько процентов медленнее (регенерация, вампиризм, за удар и убийство). */
    MAP_HERO_RECOVERY(461),
    /** Минус к потолку сопротивлений героя. */
    MAP_HERO_MAX_RESIST(462),
    /** Минус к броне, уклонению и щиту героя, в процентах. */
    MAP_HERO_DEFENCES(463),
    /** Минус к шансу блока героя. */
    MAP_HERO_BLOCK(464),
    /** Минус к шансу крита героя, в процентах от него. */
    MAP_HERO_CRIT(465),
    /** Монстры пробивают столько процентов стихийных сопротивлений. */
    MAP_MONSTER_PENETRATION(466),
    /** Монстры отражают столько процентов полученного урона. */
    MAP_MONSTER_REFLECT(467),
    /** Монстры: плюс к шансу крита. */
    MAP_MONSTER_CRITICAL(468),
    /** Монстры: плюс к шансу наложить состояние. */
    MAP_MONSTER_AILMENTS(469),
    /** Монстры: увеличение брони и уклонения, в процентах. */
    MAP_MONSTER_ARMOUR(470),
    /** Монстры крадут столько процентов урона здоровьем. */
    MAP_MONSTER_LEECH(471),
    /** Монстры избегают оглушения с таким шансом. */
    MAP_MONSTER_STUN(472),
    /** Все монстры не ниже волшебных: 1 - да. */
    MAP_MONSTER_MAGIC_MIN(473),
    /** Награды герою: быстрее ходит, быстрее бьёт, больше здоровья, крадёт здоровье. */
    MAP_HERO_HASTE(474),
    MAP_HERO_ATTACK_SPEED(475),
    MAP_HERO_LIFE(476),
    MAP_HERO_LEECH(477),
    /** Больше золота с карты, в процентах. */
    MAP_GOLD(478),
    /** Больше источников на карте, плоско. */
    MAP_FOUNTAINS(479),
    /** Босс карты сильнее и щедрее: столько процентов «больше» здоровья и урона и столько же к его добыче. */
    MAP_BOSS_POWER(481),
    /** Ещё столько кристаллов эссенций на карте, плоско (0.69.0). */
    MAP_CRYSTALS(480),
    /** Книги умений падают чаще, в процентах к шансу (0.69.0). */
    MAP_BOOKS(492),

    // ========== Ремёсла (0.37.0): инструменты и ветка дерева «Ремесло» ==========
    // Скорость цикла, процент лишней единицы (каждые 100 - гарантированная), снижение шанса
    // «ничего», опыт профессии и шанс побочной находки. Инструмент считается только в своей
    // профессии, поэтому в лист героя инструменты не входят - там только дерево.
    STOCK_WORK_SPEED(420),
    STOCK_WORK_YIELD(421),
    STOCK_WORK_LUCK(422),
    STOCK_WORK_EXPERIENCE(423),
    STOCK_WORK_FIND(424),

    // ========== Атлас (0.60.0): пассивы атласа героя, а не его лист - их читает кампания ==========
    // Всё в процентах, кроме плоских VAAL_MIN_MODS, CHESTS и FOUNTAINS. VAAL_CHANCE, FOUNTAINS,
    // PACK_SIZE и RARE_MONSTERS катает клиент: сервер отдаёт их в ответе входа на карту.
    ATLAS_QUANTITY(440),
    ATLAS_RARITY(441),
    ATLAS_EXPERIENCE(442),
    ATLAS_MAP_DROP(443),
    ATLAS_VAAL_CHANCE(444),
    ATLAS_VAAL_REWARD(445),
    ATLAS_VAAL_MIN_MODS(446),
    ATLAS_CHESTS(447),
    ATLAS_FOUNTAINS(448),
    ATLAS_PACK_SIZE(449),
    ATLAS_RARE_MONSTERS(450),
    ATLAS_BOSS_RESPAWN(451),
    ATLAS_BOSS_UNIQUE(452),
    // Атлас, продолжение (0.66.0).
    /** Выпавшая карта чаще на уровень выше, в процентах к шансу. */
    ATLAS_MAP_NEXT(482),
    /** Выпавшая карта чаще редкая, в процентах к весу. */
    ATLAS_MAP_RARE(483),
    /** Шанс лишнего аффикса на выпавшей карте, в процентах. */
    ATLAS_MAP_AFFIX(484),
    /** Эффект модификаторов карты сильнее на столько процентов - и риск, и награда. */
    ATLAS_MAP_EFFECT(485),
    /** Уникалка со стража Ваал-зоны чаще, в процентах к шансу. */
    ATLAS_VAAL_UNIQUE(486),
    /** Добыча с боссов, в процентах к количеству. */
    ATLAS_BOSS_LOOT(487),
    /** Добыча из сундуков, в процентах к количеству. */
    ATLAS_CHEST_LOOT(488),
    /** Рецепт верстака находится чаще, в процентах к шансу. */
    ATLAS_RECIPE(489),
    /** Золото на картах, в процентах. */
    ATLAS_GOLD(490),
    /** Редкие монстры несут ещё столько модификаторов. */
    ATLAS_MONSTER_MODS(491),
    // Атлас, продолжение (0.69.0): кристаллы, книги, фляги и мана.
    /** Кристаллы эссенций стоят чаще, в процентах к шансу. */
    ATLAS_CRYSTAL_CHANCE(453),
    /** Шанс лишней эссенции в кристалле, в процентах. */
    ATLAS_CRYSTAL_ESSENCES(454),
    /** Шанс, что эссенция кристалла на ступень выше, в процентах. */
    ATLAS_CRYSTAL_TIER(455),
    /** Ещё столько кристаллов в окне зоны, плоско. */
    ATLAS_CRYSTALS(456),
    /** Кристаллов больше на столько процентов. */
    ATLAS_CRYSTALS_MORE(457),
    /** Стражи кристаллов сильнее на столько процентов. */
    ATLAS_GUARDIAN_POWER(458),
    /** Книги умений падают чаще, в процентах к шансу. */
    ATLAS_BOOKS(459),
    /** Книга своего класса чаще, в процентах к её доле. */
    ATLAS_BOOKS_OWN(493),
    /** Фляги получают больше зарядов на картах, в процентах. */
    ATLAS_FLASK_CHARGES(494),
    /** Фляги действуют дольше на картах, в процентах. */
    ATLAS_FLASK_DURATION(495),
    /** Лишние заряды фляг за каждого убитого редкого, плоско. */
    ATLAS_FLASK_RARE(496),
    /** Регенерация маны на картах, в процентах. */
    ATLAS_MANA_REGEN(497),
    /** Уровни активных умений на картах, плоско. */
    ATLAS_SKILL_LEVEL(498),
    // ========== Умения класса (0.69.0): уровни и сила ==========
    /** Плюс к уровню умений: всех, атак, чар, кличей, проклятий, аур и пассивных. */
    STOCK_SKILL_LEVEL(500),
    STOCK_ATTACK_LEVEL(501),
    STOCK_SPELL_LEVEL(502),
    STOCK_WARCRY_LEVEL(503),
    STOCK_CURSE_LEVEL(504),
    STOCK_AURA_LEVEL(505),
    STOCK_PASSIVE_LEVEL(506),
    /** Эффект аур, кличей и проклятий, в процентах. */
    STOCK_AURA_EFFECT(510),
    STOCK_WARCRY_EFFECT(511),
    STOCK_CURSE_EFFECT(512),
    /** Сила лечения умений, в процентах. */
    STOCK_SKILL_HEALING(513),
    /** Ещё столько целей у атак умений, плоско. */
    STOCK_SKILL_TARGETS(514),
    /** Шанс применить готовое умение без маны, в процентах. */
    STOCK_FREE_SKILL_CHANCE(515),
    /** Урон по проклятым, в процентах. */
    STOCK_DAMAGE_VS_CURSED(516),
    /** Получив удар - шанс проклясть ударившего своим проклятием, в процентах. */
    STOCK_CURSE_ON_HIT(517),
    /** Кличи дают столько процентов скорости атаки. */
    STOCK_WARCRY_SPEED(518),
    /** Кличи восстанавливают столько процентов здоровья. */
    STOCK_WARCRY_HEAL(519),
    // ========== Фляги (0.69.0) ==========
    // Героя - пояс, эссенции, атлас: на все фляги разом. Те же статы на самой фляге - только на неё.
    STOCK_FLASK_CHARGES_GAINED(520),
    STOCK_FLASK_DURATION(521),
    STOCK_FLASK_EFFECT(522),
    /** Меньше зарядов за глоток, в процентах. */
    STOCK_FLASK_CHARGES_USED(523),
    STOCK_FLASK_LIFE_RECOVERY(524),
    /** Восстановление фляг жизни и маны, в процентах; минус - меньше. */
    STOCK_FLASK_RECOVERY(525),
    /** Лишние заряды за убийство, плоско. */
    STOCK_FLASK_CHARGES_PER_KILL(526),
    // Свойства самой фляги - база и её строки; на лист героя не входят, действуют, пока фляга выпита.
    FLASK_CHARGES(530),
    FLASK_CHARGES_PER_USE(531),
    /** Длительность действия в секундах. */
    FLASK_DURATION(532),
    /** Сколько здоровья и маны возвращает глоток. */
    FLASK_LIFE(533),
    FLASK_MANA(534),
    /** Доля восстановления сразу, в процентах; остальное - за время действия. */
    FLASK_INSTANT(535),
    /** Шанс заряда при крите и когда героя ударили, в процентах. */
    FLASK_CHARGE_ON_CRIT(536),
    FLASK_CHARGE_WHEN_HIT(537),
    /** Доля восстановленного здоровья, что идёт и в щит, и возвращается маной, в процентах. */
    FLASK_LIFE_TO_SHIELD(538),
    FLASK_LIFE_TO_MANA(539),
    /** Восстановление больше на столько процентов при здоровье ниже 35 %. */
    FLASK_LOW_LIFE_RECOVERY(540),
    /** Секунды к действию за каждое убийство во время действия. */
    FLASK_DURATION_PER_KILL(541),
    /** Шанс не потратить заряды, в процентах. */
    FLASK_NO_CHARGE_CHANCE(542),
    /** Глоток возвращает столько процентов маны и щита. */
    FLASK_SIP_MANA(543),
    FLASK_SIP_SHIELD(544),
    /** Пьётся сама, когда здоровья меньше стольких процентов. */
    FLASK_AUTO_LOW_LIFE(545),
    /** Глоток тратит все заряды: 1 - да. */
    FLASK_USES_ALL(546),
    /** Секунды неуязвимости. */
    FLASK_INVULNERABLE(547),
    /** Умения не тратят ману, пока фляга действует: 1 - да. */
    FLASK_SKILLS_FREE(548),
    /** Удары накладывают случайное проклятие класса, пока фляга действует: 1 - да. */
    FLASK_HITS_CURSE(549),
    // Невосприимчивость, пока фляга действует: 1 - да.
    STOCK_IMMUNE_BLEED(550),
    STOCK_IMMUNE_FREEZE(551),
    STOCK_IMMUNE_IGNITE(552),
    STOCK_IMMUNE_SHOCK(553),
    STOCK_IMMUNE_POISON(554),
    STOCK_IMMUNE_CURSE(555),
    STOCK_IMMUNE_STUN(556),
    // ========== Силы уникальных предметов (0.70.0) ==========
    // У каждой уникалки и мифика свои; что сила делает, описывает content/powers.json.
    POWER_THORN_LASH(600),
    POWER_DEFIANCE(601),
    POWER_SOUL_HARVEST(602),
    POWER_GRAVE_CHILL(603),
    POWER_STATIC_DODGE(604),
    POWER_SWORN_STRIKE(605),
    POWER_DESPERATE_THIRST(606),
    POWER_GHOSTLY_ENTRY(607),
    POWER_EMBER_TRAIL(608),
    POWER_MIRROR_GUARD(609),
    POWER_BORROWED_TIME(610),
    POWER_CONSTRICT(611),
    POWER_HOLLOW_VESSEL(612),
    POWER_STORM_EYE(613),
    POWER_GLUTTONY(614),
    POWER_AMBUSH(615),
    POWER_ROT_SPELL(616),
    POWER_GRIEF(617),
    POWER_AFTERSHOCK(618),
    POWER_FROST_ARROW(619),
    POWER_ASH_BURST(620),
    POWER_STOKED(621),
    POWER_SLAG_CLOG(622),
    POWER_RIVET(623),
    POWER_CRUCIBLE(624),
    POWER_QUENCH_RETURN(625),
    POWER_EMBER_WARD(626),
    POWER_TIDE_SONG(627),
    POWER_BRINE_MEND(628),
    POWER_ROOT_DRINK(629),
    POWER_BLOOD_SCENT(630),
    POWER_WARDENS_KEYS(631),
    POWER_DEATHS_VEIL(632),
    POWER_CRYSTAL_RETORT(633),
    POWER_ASHEN_END(634),
    POWER_RIME_BLOCK(635),
    POWER_DEEP_TIDE(636),
    POWER_SUNFLARE(637),
    POWER_SWARM_BURST(638),
    POWER_CINDER_PIERCE(639),
    POWER_ABYSS_DODGE(640),
    POWER_DEVOUR(641),
    POWER_WORLDEATER(642),
    POWER_ALL_SEEING(643),
    POWER_FORESIGHT(644),
    POWER_TIME_LOOP(645),
    POWER_SLOW_TIME(646),
    POWER_SOULFORGE(647),
    POWER_SOUL_SUNDER(648),
    POWER_HORIZON(649),
    POWER_ENDLESS_ROAD(650),
    POWER_EVERFLASK(651),
    POWER_FEAST(652),
    POWER_GILDED_GREED(653),
    POWER_BEAST_FURY(654),
    POWER_PREDATOR_SENSE(655),
    POWER_DEVOTED_SWIFTNESS(656),
    POWER_GLUTTONOUS_HIDE(657),
    POWER_THICK_HIDE(658),
    POWER_KAOMS_BLAZE(659),
    POWER_SEALED_HEART(660),
    POWER_CARCASS_BURST(661),
    POWER_BONE_CRUNCH(662),
    POWER_JAW_BREAKER(663),
    POWER_VIRTUOSO_TEMPO(664),
    POWER_ACUITY_DRAIN(665),
    POWER_WANDERERS_START(666),
    POWER_LEAGUE_STRIDE(667),
    POWER_FLEETING_STEP(668),
    POWER_WYRM_HOARD(669),
    POWER_DESERT_WIND(670),
    POWER_SEVENTH_VEIL(671),
    POWER_MAW_HUNGER(672),
    POWER_GIANT_BLOOD(673),
    POWER_HEADHUNT(674),
    POWER_TROPHY_HUNTER(675),
    POWER_MAGNATE_TAX(676),
    POWER_BEREKS_STORM(677),
    POWER_BEREKS_FROST(678),
    POWER_VENTORS_WAGER(679),
    POWER_KAOMS_RAGE(680),
    POWER_ASTRAL_HARMONY(681),
    POWER_CARNAGE(682),
    POWER_BISCOS_FIND(683),
    POWER_REMORSEFUL_GUARD(684),
    POWER_PHOENIX_REBIRTH(685),
    POWER_SAFFELL_PRISM(686),
    POWER_DRILL_SHOT(687),
    POWER_REARGUARD(688),
    POWER_HYRRIS_TOXIN(689),
    POWER_BINOS_SPREAD(690),
    POWER_CATALYST_SURGE(691),
    POWER_LIONEYES_AIM(692),
    POWER_MAROHI_CRUSH(693),
    POWER_STARFORGED(694),
    POWER_HEGEMONY(695),
    POWER_TIDE_TURN(696),
    POWER_UNDERTOW(697),
    POWER_BRINESHELL(698),
    POWER_SALT_SPINES(699),
    POWER_MIRE_BLOOD(700),
    POWER_BOG_BREATH(701),
    POWER_PACK_TACTICS(702),
    POWER_ALPHA_HOWL(703),
    POWER_WARDENS_VOW(704),
    POWER_GRAVE_PACT(705),
    POWER_BONE_TITHE(706),
    POWER_SHARD_SPLINTER(707),
    POWER_EMBERHEART(708),
    POWER_CINDER_SKIN(709),
    POWER_WINTER_GRIP(710),
    POWER_SHATTER(711),
    POWER_LEVIATHAN_BITE(712),
    POWER_FORGE_FOCUS(713),
    POWER_ANVIL_HEART(714),
    POWER_EMBER_TOUCH(715),
    POWER_IRON_STANCE(716),
    POWER_CHAINBIND(717),
    POWER_FORGE_BULWARK(718),
    POWER_SMELT(719),
    POWER_MOUNTAIN_BREAK(720),
    POWER_TEMPERED_TIPS(721),
    POWER_SMITH_TEMPER(722),
    POWER_QUENCH(723),
    POWER_FORGE_GALE(724),
    POWER_BRAZEN_CALL(725),
    POWER_WAR_SHOUT(726),
    POWER_UNYIELDING(727),
    POWER_LAST_STAND(728),
    POWER_SOLAR_FLARE(729),
    POWER_SUNS_BLESSING(730),
    POWER_ETERNAL_SANDS(731),
    POWER_TIMELESS(732),
    POWER_ROYAL_VENOM(733),
    POWER_HIVEMIND(734),
    POWER_SOVEREIGN_BRAND(735),
    POWER_MOLTEN_CORE(736),
    POWER_ABYSSAL_GAZE(737),
    POWER_ABYSS_CALL(738),
    POWER_VOID_HUNGER(739),
    POWER_SAINTS_GRACE(740),
    POWER_MIRROR_RIPOSTE(741),
    POWER_SEETHING_RAGE(742),
    POWER_STARLIGHT_WARD(743),
    POWER_ATZIRIS_VOW(744),
    POWER_VOID_SIGHT(745),
    POWER_VOID_ECHO(746),
    POWER_ETERNITY_SHROUD(747),
    POWER_TIMELESS_BODY(748),
    POWER_WORLDBREAK(749),
    POWER_TITAN_GRIP(750),
    POWER_ENDLESS_STRIDE(751),
    POWER_PHASE_STEP(752),
    POWER_DAWNBURST(753),
    POWER_FIRST_LIGHT(754),
    POWER_FIRST_BLOOD(755),
    POWER_BLOOD_FRENZY(756),
    POWER_METEOR(757),
    POWER_STARSHARD(758),
    POWER_ABYSSAL_TIDE(759),
    POWER_DEEP_MIND(760),
    POWER_ABYSSAL_HEART(761),
    POWER_HEARTBEAT(762),
    POWER_ABYSS_WILL(763),
    POWER_TITANS_MIGHT(764),
    POWER_TITAN_SKIN(765),
    POWER_STORM_VOLLEY(766),
    POWER_STORMCALL(767),
    POWER_FALLEN_GRACE(768),
    POWER_DIVINE_FALL(769),
    POWER_GODS_BOUNTY(770),
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
