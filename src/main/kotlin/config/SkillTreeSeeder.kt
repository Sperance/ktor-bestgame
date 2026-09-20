package config

import application.enums.EnumSkillNodeType
import application.enums.EnumSkillNodeType.KEYSTONE
import application.enums.EnumSkillNodeType.NOTABLE
import application.enums.EnumSkillNodeType.SMALL
import application.enums.EnumSkillNodeType.START
import base.exception.model.ModifierExceptions
import extensions.toStableObjectId
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.skilltree.SkillTreeNode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Начальные данные коллекции `SkillTreeNode` - дерево навыков в духе POE.
 *
 * Форма повторяет настоящее дерево: шесть классовых областей стоят по кругу
 * через шестьдесят градусов, Скион находится ровно в центре, а между
 * областями проходит внутреннее кольцо - именно через него персонаж попадает
 * в чужую часть дерева, потому что стартовый узел другого класса взять нельзя.
 *
 * Из каждой области расходятся четыре ветки: малые узлы, нотабль в конце, а у
 * части веток ещё и кейстоун, который меняет правила и берёт за это плату.
 * Узлов в дереве заметно больше, чем очков у сотого уровня, поэтому взять
 * всё нельзя - как и в POE, приходится выбирать.
 *
 * Названия узлов и их состав взяты из дерева Path of Exile (данные
 * PathOfBuilding, `TreeData/3_26/tree.lua`) и переложены на статы проекта.
 * Бонусы фиксированы: они ссылаются на описания модификаторов
 * из `ModifierDefinition`, но значения задаёт само дерево, а не тир.
 */
object SkillTreeSeeder {

    // ==================== Раскладка ====================

    /**
     * Радиус, на котором стоят стартовые узлы классов и внутреннее кольцо.
     */
    private const val CLASS_RADIUS = 340

    /**
     * Расстояние между соседними узлами одной ветки.
     */
    private const val BRANCH_STEP = 115

    /**
     * Угол между ветками одной области, градусы.
     */
    private const val BRANCH_SPREAD = 15.0

    /**
     * Шаг ветки Скиона: она идёт из центра наружу и должна уложиться
     * внутри кольца.
     */
    private const val SCION_STEP = 85

    /**
     * Угол между соседними классами: шесть областей на полный круг.
     */
    private const val SECTOR = 60.0

    // ==================== Описание узлов ====================

    /**
     * Бонус узла: код описания модификатора и его значения,
     * по одному на каждый эффект описания.
     */
    private data class NodeBonus(val code: String, val values: List<Double>)

    private fun mod(code: String, vararg values: Double) = NodeBonus(code, values.toList())

    /**
     * Узел до раскладки по координатам: положение он получает от своей ветки.
     */
    private data class NodeTemplate(
        val code: String,
        val name: String,
        val type: EnumSkillNodeType,
        val bonuses: List<NodeBonus>,
        val description: String? = null,
    )

    private fun small(code: String, name: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, SMALL, bonuses.toList())

    private fun notable(code: String, name: String, description: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, NOTABLE, bonuses.toList(), description)

    private fun keystone(code: String, name: String, description: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, KEYSTONE, bonuses.toList(), description)

    /**
     * Область класса: стартовый узел и ровно [BRANCHES_PER_AREA] веток от него.
     *
     * Ветки перечислены по возрастанию угла - левая смотрит на предыдущего
     * соседа по кругу, правая на следующего.
     */
    private data class ClassArea(
        val startCode: String,
        val startName: String,
        val description: String,
        val angle: Double,
        val branches: List<List<NodeTemplate>>,
    ) {
        init {
            require(branches.size == BRANCHES_PER_AREA) { "$startCode: области нужны ровно $BRANCHES_PER_AREA ветки" }
        }
    }

    /**
     * Ветка Скиона: идёт из центра к узлу кольца с индексом [ringIndex].
     */
    private data class ScionBranch(val ringIndex: Int, val nodes: List<NodeTemplate>)

    private const val BRANCHES_PER_AREA = 4
    private const val SCION_START = "SCION_START"

    // ==================== Кейстоуны ====================

    private val resoluteTechnique = keystone(
        "KEYSTONE_RESOLUTE_TECHNIQUE", "Resolute Technique",
        "Непреклонная техника: критических ударов больше не будет, зато физический урон растёт",
        mod("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", 0.0), mod("PASSIVE_MORE_PHYSICAL_DAMAGE", 20.0)
    )

    private val unwaveringStance = keystone(
        "KEYSTONE_UNWAVERING_STANCE", "Unwavering Stance",
        "Непоколебимая стойка: уклоняться нельзя, но оглушить почти невозможно",
        mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_STUN_THRESHOLD", 100.0)
    )

    private val bloodMagic = keystone(
        "KEYSTONE_BLOOD_MAGIC", "Blood Magic",
        "Кровавая магия: маны нет совсем, запас здоровья вырастает",
        mod("PASSIVE_SET_MAXIMUM_MANA", 0.0), mod("PASSIVE_MORE_MAXIMUM_LIFE", 20.0)
    )

    private val ironReflexes = keystone(
        "KEYSTONE_IRON_REFLEXES", "Iron Reflexes",
        "Железные рефлексы: уклонения нет, зато брони вдвое больше",
        mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_ARMOUR", 100.0)
    )

    private val acrobatics = keystone(
        "KEYSTONE_ACROBATICS", "Acrobatics",
        "Акробатика: уклонение растёт, броня почти пропадает",
        mod("PASSIVE_MORE_EVASION_RATING", 30.0), mod("PASSIVE_MORE_ARMOUR", -50.0)
    )

    private val chaosInoculation = keystone(
        "KEYSTONE_CHAOS_INOCULATION", "Chaos Inoculation",
        "Прививка от хаоса: здоровья остаётся единица, но хаос больше не берёт",
        mod("PASSIVE_SET_MAXIMUM_LIFE", 1.0), mod("PASSIVE_SET_CHAOS_RESISTANCE", 100.0)
    )

    private val vaalPact = keystone(
        "KEYSTONE_VAAL_PACT", "Vaal Pact",
        "Договор ваал: регенерация здоровья не работает, зато вампиризм вдвое быстрее",
        mod("PASSIVE_SET_LIFE_REGENERATION", 0.0), mod("PASSIVE_MORE_LIFE_LEECH", 100.0)
    )

    private val elementalOverload = keystone(
        "KEYSTONE_ELEMENTAL_OVERLOAD", "Elemental Overload",
        "Стихийная перегрузка: критические удары не наносят урона, но стихии бьют куда сильнее",
        mod("PASSIVE_SET_CRITICAL_DAMAGE", 0.0), mod("PASSIVE_MORE_ELEMENTAL_DAMAGE", 40.0, 40.0, 40.0)
    )

    // ==================== Области классов ====================

    /**
     * Шесть классовых областей по кругу, по возрастанию угла.
     * Угол отсчитывается как на экране: ноль вправо, девяносто вниз.
     */
    private val areas = listOf(

        // ---------- Ведьма: интеллект ----------
        ClassArea(
            startCode = "INT_START",
            startName = "Way of the Witch",
            description = "Начало области Ведьмы: мана, энергощит и заклинания",
            angle = -90.0,
            branches = listOf(
                listOf(
                    small("INT_WARD_1", "Energy Shield", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    small("INT_WARD_2", "Elemental Ward", mod("ADD_ALL_ELEMENTAL_RESISTANCES", 10.0)),
                    small("INT_WARD_3", "Energy Shield", mod("ADD_ENERGY_SHIELD", 20.0)),
                    notable(
                        "INT_WARD_NOTABLE", "Foresight",
                        "Предвидение: запас энергощита и его прирост",
                        mod("ADD_ENERGY_SHIELD", 20.0), mod("INCREASED_ENERGY_SHIELD", 14.0)
                    ),
                    chaosInoculation,
                ),
                listOf(
                    small("INT_ARCANE_1", "Intelligence", mod("ADD_INTELLIGENCE", 10.0)),
                    small("INT_ARCANE_2", "Intelligence", mod("ADD_INTELLIGENCE", 10.0)),
                    small("INT_ARCANE_3", "Spell Damage", mod("INCREASED_SPELL_DAMAGE", 8.0)),
                    notable(
                        "INT_ARCANE_NOTABLE", "Arcanist's Dominion",
                        "Власть чародея: урон заклинаний, скорость применения и интеллект",
                        mod("INCREASED_SPELL_DAMAGE", 20.0), mod("INCREASED_CAST_SPEED", 5.0),
                        mod("ADD_INTELLIGENCE", 20.0)
                    ),
                ),
                listOf(
                    small("INT_AETHER_1", "Mana", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_AETHER_2", "Mana", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_AETHER_3", "Mana Regeneration", mod("INCREASED_MANA_REGENERATION", 15.0)),
                    notable(
                        "INT_AETHER_NOTABLE", "Deep Wisdom",
                        "Глубокая мудрость: энергощит, мана и интеллект",
                        mod("ADD_ENERGY_SHIELD", 20.0), mod("ADD_MAXIMUM_MANA", 20.0),
                        mod("ADD_INTELLIGENCE", 20.0)
                    ),
                ),
                listOf(
                    small("INT_SOUL_1", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("INT_SOUL_2", "Mana", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_SOUL_3", "Energy Shield", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    notable(
                        "INT_SOUL_NOTABLE", "Heart and Soul",
                        "Сердце и душа: запас здоровья и маны",
                        mod("INCREASED_MAXIMUM_LIFE", 8.0), mod("INCREASED_MAXIMUM_MANA", 12.0)
                    ),
                ),
            )
        ),

        // ---------- Тень: ловкость и интеллект ----------
        ClassArea(
            startCode = "DEX_INT_START",
            startName = "Way of the Shadow",
            description = "Начало области Тени: криты, уклонение и хаос",
            angle = -30.0,
            branches = listOf(
                listOf(
                    small("DEX_INT_VENOM_1", "Chaos Resistance", mod("ADD_CHAOS_RESISTANCE", 8.0)),
                    small("DEX_INT_VENOM_2", "Energy Shield", mod("ADD_ENERGY_SHIELD", 20.0)),
                    small("DEX_INT_VENOM_3", "Chaos Damage", mod("ADD_CHAOS_DAMAGE", 6.0)),
                    notable(
                        "DEX_INT_VENOM_NOTABLE", "Wasting",
                        "Увядание: урон хаосом и сопротивление ему",
                        mod("ADD_CHAOS_DAMAGE", 12.0), mod("ADD_CHAOS_RESISTANCE", 17.0)
                    ),
                ),
                listOf(
                    small(
                        "DEX_INT_TRICKERY_1", "Dexterity and Intelligence",
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    small("DEX_INT_TRICKERY_2", "Critical Strike Chance", mod("INCREASED_CRITICAL_STRIKE_CHANCE", 10.0)),
                    small("DEX_INT_TRICKERY_3", "Critical Strike Multiplier", mod("ADD_CRITICAL_STRIKE_MULTIPLIER", 10.0)),
                    notable(
                        "DEX_INT_TRICKERY_NOTABLE", "Trickery",
                        "Хитрость: шанс критического удара и оба атрибута Тени",
                        mod("INCREASED_CRITICAL_STRIKE_CHANCE", 20.0),
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                ),
                listOf(
                    small("DEX_INT_COORD_1", "Attack Speed", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    small("DEX_INT_COORD_2", "Cast Speed", mod("INCREASED_CAST_SPEED", 3.0)),
                    small(
                        "DEX_INT_COORD_3", "Evasion and Energy Shield",
                        mod("INCREASED_EVASION_AND_ENERGY_SHIELD", 8.0, 8.0)
                    ),
                    notable(
                        "DEX_INT_COORD_NOTABLE", "Coordination",
                        "Координация: скорость атаки, скорость заклинаний и оба атрибута Тени",
                        mod("INCREASED_ATTACK_SPEED", 10.0), mod("INCREASED_CAST_SPEED", 8.0),
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    acrobatics,
                ),
                listOf(
                    small("DEX_INT_MELDING_1", "Evasion", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_INT_MELDING_2", "Energy Shield", mod("ADD_ENERGY_SHIELD", 20.0)),
                    small("DEX_INT_MELDING_3", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "DEX_INT_MELDING_NOTABLE", "Melding",
                        "Слияние: запас здоровья и часть его как энергощит",
                        mod("INCREASED_MAXIMUM_LIFE", 7.0), mod("ADD_ENERGY_SHIELD", 20.0)
                    ),
                ),
            )
        ),

        // ---------- Охотница: ловкость ----------
        ClassArea(
            startCode = "DEX_START",
            startName = "Way of the Ranger",
            description = "Начало области Охотницы: уклонение и скорость",
            angle = 30.0,
            branches = listOf(
                listOf(
                    small("DEX_EVADE_1", "Evasion", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_EVADE_2", "Evasion", mod("ADD_EVASION_RATING", 20.0)),
                    small("DEX_EVADE_3", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "DEX_EVADE_NOTABLE", "Reflexes",
                        "Рефлексы: запас уклонения и его прирост",
                        mod("ADD_EVASION_RATING", 100.0), mod("INCREASED_EVASION_RATING", 30.0)
                    ),
                ),
                listOf(
                    small("DEX_SWIFT_1", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_SWIFT_2", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_SWIFT_3", "Attack Speed", mod("INCREASED_ATTACK_SPEED", 4.0)),
                    notable(
                        "DEX_SWIFT_NOTABLE", "Finesse",
                        "Виртуозность: скорость атаки и ловкость",
                        mod("INCREASED_ATTACK_SPEED", 8.0), mod("ADD_DEXTERITY", 20.0)
                    ),
                ),
                listOf(
                    small("DEX_VITALITY_1", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("DEX_VITALITY_2", "Life Leech", mod("ADD_PHYSICAL_LIFE_LEECH", 0.2)),
                    small("DEX_VITALITY_3", "Life Regeneration", mod("ADD_LIFE_REGENERATION", 2.0)),
                    notable(
                        "DEX_VITALITY_NOTABLE", "Heart of Oak",
                        "Дубовое сердце: запас здоровья и устойчивость к оглушению",
                        mod("INCREASED_MAXIMUM_LIFE", 8.0), mod("INCREASED_STUN_THRESHOLD", 20.0)
                    ),
                    vaalPact,
                ),
                listOf(
                    small("DEX_INCLINATION_1", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_INCLINATION_2", "Evasion", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_INCLINATION_3", "Physical Damage", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    notable(
                        "DEX_INCLINATION_NOTABLE", "Deadly Inclinations",
                        "Смертельные наклонности: уклонение, здоровье, урон и ловкость",
                        mod("INCREASED_EVASION_RATING", 18.0), mod("ADD_MAXIMUM_LIFE", 12.0),
                        mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("ADD_DEXTERITY", 30.0)
                    ),
                ),
            )
        ),

        // ---------- Дуэлянт: сила и ловкость ----------
        ClassArea(
            startCode = "STR_DEX_START",
            startName = "Way of the Duelist",
            description = "Начало области Дуэлянта: ближний бой, блок и скорость атаки",
            angle = 90.0,
            branches = listOf(
                listOf(
                    small("STR_DEX_GLADIATOR_1", "Attack Speed", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    small("STR_DEX_GLADIATOR_2", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
                    small("STR_DEX_GLADIATOR_3", "Attack Speed", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    notable(
                        "STR_DEX_GLADIATOR_NOTABLE", "Art of the Gladiator",
                        "Искусство гладиатора: скорость атаки и ловкость",
                        mod("INCREASED_ATTACK_SPEED", 10.0), mod("ADD_DEXTERITY", 20.0)
                    ),
                ),
                listOf(
                    small(
                        "STR_DEX_ARENA_1", "Strength and Dexterity",
                        mod("ADD_STRENGTH_AND_DEXTERITY", 10.0, 10.0)
                    ),
                    small("STR_DEX_ARENA_2", "Melee Physical Damage", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    small("STR_DEX_ARENA_3", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_DEX_ARENA_NOTABLE", "Master of the Arena",
                        "Хозяин арены: регенерация, физический урон и сила",
                        mod("ADD_LIFE_REGENERATION", 2.0), mod("INCREASED_PHYSICAL_DAMAGE", 10.0),
                        mod("ADD_STRENGTH", 20.0)
                    ),
                ),
                listOf(
                    small("STR_DEX_GUARD_1", "Block", mod("ADD_BLOCK_CHANCE", 3.0)),
                    small(
                        "STR_DEX_GUARD_2", "Armour and Evasion",
                        mod("INCREASED_ARMOUR_AND_EVASION", 12.0, 12.0)
                    ),
                    small("STR_DEX_GUARD_3", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_DEX_GUARD_NOTABLE", "Bravery",
                        "Отвага: броня с уклонением и запас здоровья",
                        mod("INCREASED_ARMOUR_AND_EVASION", 24.0, 24.0), mod("INCREASED_MAXIMUM_LIFE", 8.0)
                    ),
                    ironReflexes,
                ),
                listOf(
                    small("STR_DEX_STEEL_1", "Block", mod("ADD_BLOCK_CHANCE", 3.0)),
                    small("STR_DEX_STEEL_2", "Armour", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_DEX_STEEL_3", "Strength", mod("ADD_STRENGTH", 10.0)),
                    notable(
                        "STR_DEX_STEEL_NOTABLE", "Command of Steel",
                        "Власть стали: шанс блока и броня",
                        mod("ADD_BLOCK_CHANCE", 8.0), mod("INCREASED_ARMOUR", 15.0)
                    ),
                ),
            )
        ),

        // ---------- Мародёр: сила ----------
        ClassArea(
            startCode = "STR_START",
            startName = "Way of the Marauder",
            description = "Начало области Мародёра: здоровье, броня и физический урон",
            angle = 150.0,
            branches = listOf(
                listOf(
                    small("STR_BULWARK_1", "Armour", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_BULWARK_2", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_BULWARK_3", "Armour", mod("INCREASED_ARMOUR", 8.0)),
                    notable(
                        "STR_BULWARK_NOTABLE", "Way of the Warrior",
                        "Путь воина: урон, броня, здоровье и много силы",
                        mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("INCREASED_ARMOUR", 16.0),
                        mod("ADD_MAXIMUM_LIFE", 16.0), mod("ADD_STRENGTH", 30.0)
                    ),
                    unwaveringStance,
                ),
                listOf(
                    small("STR_MIGHT_1", "Strength", mod("ADD_STRENGTH", 10.0)),
                    small("STR_MIGHT_2", "Strength", mod("ADD_STRENGTH", 10.0)),
                    small("STR_MIGHT_3", "Physical Damage", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    notable(
                        "STR_MIGHT_NOTABLE", "Born to Fight",
                        "Рождён для боя: скорость атаки, сила и физический урон",
                        mod("INCREASED_ATTACK_SPEED", 4.0), mod("ADD_STRENGTH", 20.0),
                        mod("INCREASED_PHYSICAL_DAMAGE", 26.0)
                    ),
                    resoluteTechnique,
                ),
                listOf(
                    small("STR_BLOOD_1", "Life Regeneration", mod("ADD_LIFE_REGENERATION", 2.0)),
                    small("STR_BLOOD_2", "Life Leech", mod("ADD_PHYSICAL_LIFE_LEECH", 0.2)),
                    small("STR_BLOOD_3", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_BLOOD_NOTABLE", "Warrior's Blood",
                        "Кровь воина: регенерация, устойчивость к оглушению и сила",
                        mod("ADD_LIFE_REGENERATION", 4.0), mod("INCREASED_STUN_THRESHOLD", 20.0),
                        mod("ADD_STRENGTH", 20.0)
                    ),
                    bloodMagic,
                ),
                listOf(
                    small("STR_CONSTITUTION_1", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_CONSTITUTION_2", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_CONSTITUTION_3", "Strength", mod("ADD_STRENGTH", 10.0)),
                    notable(
                        "STR_CONSTITUTION_NOTABLE", "Constitution",
                        "Телосложение: запас здоровья и его прирост",
                        mod("ADD_MAXIMUM_LIFE", 20.0), mod("INCREASED_MAXIMUM_LIFE", 10.0)
                    ),
                ),
            )
        ),

        // ---------- Храмовник: сила и интеллект ----------
        ClassArea(
            startCode = "STR_INT_START",
            startName = "Way of the Templar",
            description = "Начало области Храмовника: броня, энергощит и стихии",
            angle = 210.0,
            branches = listOf(
                listOf(
                    small(
                        "STR_INT_DEVOTION_1", "Strength and Intelligence",
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    small("STR_INT_DEVOTION_2", "Armour", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_INT_DEVOTION_3", "Energy Shield", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    notable(
                        "STR_INT_DEVOTION_NOTABLE", "Sanctity",
                        "Святость: броня, энергощит и регенерация",
                        mod("INCREASED_ARMOUR", 20.0), mod("INCREASED_ENERGY_SHIELD", 10.0),
                        mod("ADD_LIFE_REGENERATION", 2.0),
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                ),
                listOf(
                    small("STR_INT_RETRIBUTION_1", "Spell Damage", mod("INCREASED_SPELL_DAMAGE", 8.0)),
                    small(
                        "STR_INT_RETRIBUTION_2", "Attack and Cast Speed",
                        mod("INCREASED_ATTACK_AND_CAST_SPEED", 3.0, 3.0)
                    ),
                    small(
                        "STR_INT_RETRIBUTION_3", "Strength and Intelligence",
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    notable(
                        "STR_INT_RETRIBUTION_NOTABLE", "Retribution",
                        "Возмездие: урон заклинаний и скорость атаки с применением",
                        mod("INCREASED_SPELL_DAMAGE", 15.0),
                        mod("INCREASED_ATTACK_AND_CAST_SPEED", 5.0, 5.0),
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    elementalOverload,
                ),
                listOf(
                    small("STR_INT_FAITH_1", "Mana Regeneration", mod("INCREASED_MANA_REGENERATION", 20.0)),
                    small("STR_INT_FAITH_2", "Life", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_INT_FAITH_3", "Mana", mod("ADD_MAXIMUM_MANA", 20.0)),
                    notable(
                        "STR_INT_FAITH_NOTABLE", "Quick Recovery",
                        "Быстрое восстановление: здоровье, регенерация маны и жизни",
                        mod("INCREASED_MAXIMUM_LIFE", 7.0), mod("INCREASED_MANA_REGENERATION", 20.0),
                        mod("ADD_LIFE_REGENERATION", 2.0)
                    ),
                ),
                listOf(
                    small("STR_INT_ELEMENTS_1", "Fire Resistance", mod("ADD_FIRE_RESISTANCE", 15.0)),
                    small("STR_INT_ELEMENTS_2", "Cold Resistance", mod("ADD_COLD_RESISTANCE", 15.0)),
                    small("STR_INT_ELEMENTS_3", "Lightning Resistance", mod("ADD_LIGHTNING_RESISTANCE", 15.0)),
                    notable(
                        "STR_INT_ELEMENTS_NOTABLE", "Ash, Frost and Storm",
                        "Пепел, мороз и буря: стихийный урон и сопротивления",
                        mod("INCREASED_ELEMENTAL_DAMAGE", 30.0, 30.0, 30.0),
                        mod("ADD_ALL_ELEMENTAL_RESISTANCES", 10.0)
                    ),
                ),
            )
        ),
    )

    // ==================== Внутреннее кольцо ====================

    /**
     * Узлы кольца между соседними областями. Индекс совпадает с [areas]:
     * кольцо с номером i стоит между областями i и i+1.
     */
    private val ring = listOf(
        small("RING_INT_DEX_INT", "Intelligence", mod("ADD_INTELLIGENCE", 10.0)),
        small("RING_DEX_INT_DEX", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
        small("RING_DEX_STR_DEX", "Dexterity", mod("ADD_DEXTERITY", 10.0)),
        small("RING_STR_DEX_STR", "Strength", mod("ADD_STRENGTH", 10.0)),
        small("RING_STR_STR_INT", "Strength", mod("ADD_STRENGTH", 10.0)),
        small("RING_STR_INT_INT", "Intelligence", mod("ADD_INTELLIGENCE", 10.0)),
    )

    // ==================== Область Скиона ====================

    /**
     * Скион стоит в центре круга: его ветки идут наружу и упираются в кольцо,
     * поэтому из центра открыт вход в любую часть дерева.
     */
    private val scionBranches = listOf(
        ScionBranch(
            ringIndex = 1,
            nodes = listOf(
                small(
                    "SCION_HUNTER_1", "Attack Speed and Dexterity",
                    mod("INCREASED_ATTACK_SPEED", 4.0), mod("ADD_DEXTERITY", 5.0)
                ),
                small(
                    "SCION_HUNTER_2", "Evasion and Life",
                    mod("ADD_EVASION_RATING", 20.0), mod("ADD_MAXIMUM_LIFE", 14.0)
                ),
                notable(
                    "SCION_HUNTER_NOTABLE", "Path of the Hunter",
                    "Путь охотника: урон и ловкость",
                    mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("ADD_DEXTERITY", 20.0)
                ),
            )
        ),
        ScionBranch(
            ringIndex = 3,
            nodes = listOf(
                small(
                    "SCION_WARRIOR_1", "Life and Strength",
                    mod("ADD_MAXIMUM_LIFE", 12.0), mod("ADD_STRENGTH", 5.0)
                ),
                small(
                    "SCION_WARRIOR_2", "Life and Armour",
                    mod("ADD_ARMOUR", 20.0), mod("ADD_MAXIMUM_LIFE", 16.0)
                ),
                notable(
                    "SCION_WARRIOR_NOTABLE", "Path of the Warrior",
                    "Путь воина: броня, сила и физический урон",
                    mod("ADD_ARMOUR", 50.0), mod("ADD_STRENGTH", 20.0),
                    mod("INCREASED_PHYSICAL_DAMAGE", 16.0)
                ),
            )
        ),
        ScionBranch(
            ringIndex = 5,
            nodes = listOf(
                small(
                    "SCION_SAVANT_1", "Spell Damage and Intelligence",
                    mod("INCREASED_SPELL_DAMAGE", 10.0), mod("ADD_INTELLIGENCE", 5.0)
                ),
                small(
                    "SCION_SAVANT_2", "Mana Regeneration and Life",
                    mod("INCREASED_MANA_REGENERATION", 20.0), mod("ADD_MAXIMUM_LIFE", 14.0)
                ),
                notable(
                    "SCION_SAVANT_NOTABLE", "Path of the Savant",
                    "Путь мудреца: урон заклинаний, мана и интеллект",
                    mod("INCREASED_SPELL_DAMAGE", 16.0), mod("ADD_MAXIMUM_MANA", 20.0),
                    mod("ADD_INTELLIGENCE", 20.0)
                ),
            )
        ),
    )

    // ==================== Раскладка в координаты и рёбра ====================

    /**
     * Узел, уже получивший место на плоскости.
     */
    private data class PlacedNode(val template: NodeTemplate, val x: Int, val y: Int)

    /**
     * Готовое дерево до превращения в документы: узлы с координатами
     * и рёбра между ними.
     */
    private data class Layout(val nodes: List<PlacedNode>, val links: List<Pair<String, String>>)

    private fun polar(angle: Double, radius: Int): Pair<Int, Int> {
        val radians = Math.toRadians(angle)
        return (cos(radians) * radius).roundToInt() to (sin(radians) * radius).roundToInt()
    }

    /**
     * Собирает дерево: области по кругу, кольцо между ними и центр Скиона.
     */
    private fun buildLayout(): Layout {
        val nodes = mutableListOf<PlacedNode>()
        val links = mutableListOf<Pair<String, String>>()

        fun place(template: NodeTemplate, angle: Double, radius: Int) {
            val (x, y) = polar(angle, radius)
            nodes += PlacedNode(template, x, y)
        }

        // Классовые области: стартовый узел на круге, ветки расходятся наружу
        areas.forEach { area ->
            place(NodeTemplate(area.startCode, area.startName, START, emptyList(), area.description), area.angle, CLASS_RADIUS)

            area.branches.forEachIndexed { index, branch ->
                val angle = area.angle + (index - (area.branches.size - 1) / 2.0) * BRANCH_SPREAD
                var previous = area.startCode

                branch.forEachIndexed { step, node ->
                    place(node, angle, CLASS_RADIUS + BRANCH_STEP * (step + 1))
                    links += previous to node.code
                    previous = node.code
                }
            }

            // Первые узлы веток связаны между собой дугой сразу за стартом.
            // Без неё область была бы звездой, и пришедший по кольцу чужак
            // упирался бы в стартовый узел класса, который взять нельзя.
            area.branches.map { it.first().code }.zipWithNext { left, right ->
                links += left to right
            }
        }

        // Кольцо: связывает соседние области между собой и замыкается само на себя.
        // Заходить в чужую область приходится именно через него: стартовый узел
        // другого класса взять нельзя.
        areas.forEachIndexed { index, area ->
            val next = areas[(index + 1) % areas.size]
            val node = ring[index]

            place(node, area.angle + SECTOR / 2, CLASS_RADIUS)

            links += area.startCode to node.code
            links += next.startCode to node.code
            links += area.branches.last().first().code to node.code
            links += next.branches.first().first().code to node.code
            links += node.code to ring[(index + 1) % ring.size].code
        }

        // Скион: центр круга и три ветки из него к кольцу
        nodes += PlacedNode(
            NodeTemplate(
                SCION_START, "Way of the Scion", START, emptyList(),
                "Начало области Скиона: центр дерева, откуда открыт вход в любую ветку"
            ),
            0, 0
        )

        scionBranches.forEach { branch ->
            val angle = areas[branch.ringIndex].angle + SECTOR / 2
            var previous = SCION_START

            branch.nodes.forEachIndexed { step, node ->
                place(node, angle, SCION_STEP * (step + 1))
                links += previous to node.code
                previous = node.code
            }

            links += previous to ring[branch.ringIndex].code
        }

        return Layout(nodes, links)
    }

    // ==================== Генерация документов ====================

    /**
     * Документы коллекции `SkillTreeNode`.
     *
     * @param definitions описания модификаторов - из них берутся _id бонусов
     */
    fun seed(definitions: List<ModifierDefinition>): List<SkillTreeNode> {
        val byCode = definitions.associateBy { it.code }
        val layout = buildLayout()
        val connections = buildConnections(layout.links)

        return layout.nodes.map { placed ->
            val template = placed.template

            SkillTreeNode(
                code = template.code,
                name = template.name,
                type = template.type,
                params = template.bonuses.mapTo(mutableListOf()) { bonus ->
                    val definition = byCode[bonus.code]
                        ?: throw ModifierExceptions.funExceptionCodeNotFound("seed", bonus.code)
                    Modifier.passive(definition._id, bonus.values)
                },
                connections = connections[template.code].orEmpty().toMutableList(),
                // Стартовый узел класса персонаж получает при создании и не платит за него
                cost = if (template.type == START) 0 else 1,
                positionX = placed.x,
                positionY = placed.y,
                description = template.description,
                // Дерево пересевается на каждом старте: _id должен быть стабильным
                _id = template.code.toStableObjectId()
            )
        }
    }

    /**
     * Рёбра, разложенные по узлам. Каждое ребро попадает в оба конца,
     * чтобы документ дерева читался без обратного поиска.
     *
     * Дубликаты снимаются: кольцо объявляет свои связи с обеих сторон,
     * поэтому одна и та же пара может прийти дважды.
     */
    private fun buildConnections(links: List<Pair<String, String>>): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()

        links.forEach { (from, to) ->
            result.getOrPut(from) { linkedSetOf() }.add(to)
            result.getOrPut(to) { linkedSetOf() }.add(from)
        }

        return result.mapValues { it.value.toList() }
    }
}
