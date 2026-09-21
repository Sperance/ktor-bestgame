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
        val type: EnumSkillNodeType,
        val bonuses: List<NodeBonus>,
    )

    private fun small(code: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, SMALL, bonuses.toList())

    private fun notable(code: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, NOTABLE, bonuses.toList())

    private fun keystone(code: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, KEYSTONE, bonuses.toList())

    /**
     * Область класса: стартовый узел и ровно [BRANCHES_PER_AREA] веток от него.
     *
     * Ветки перечислены по возрастанию угла - левая смотрит на предыдущего
     * соседа по кругу, правая на следующего.
     */
    private data class ClassArea(
        val startCode: String,
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
        "KEYSTONE_RESOLUTE_TECHNIQUE",
        mod("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", 0.0), mod("PASSIVE_MORE_PHYSICAL_DAMAGE", 20.0)
    )

    private val unwaveringStance = keystone(
        "KEYSTONE_UNWAVERING_STANCE",
        mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_STUN_THRESHOLD", 100.0)
    )

    private val bloodMagic = keystone(
        "KEYSTONE_BLOOD_MAGIC",
        mod("PASSIVE_SET_MAXIMUM_MANA", 0.0), mod("PASSIVE_MORE_MAXIMUM_LIFE", 20.0)
    )

    private val ironReflexes = keystone(
        "KEYSTONE_IRON_REFLEXES",
        mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_ARMOUR", 100.0)
    )

    private val acrobatics = keystone(
        "KEYSTONE_ACROBATICS",
        mod("PASSIVE_MORE_EVASION_RATING", 30.0), mod("PASSIVE_MORE_ARMOUR", -50.0)
    )

    private val chaosInoculation = keystone(
        "KEYSTONE_CHAOS_INOCULATION",
        mod("PASSIVE_SET_MAXIMUM_LIFE", 1.0), mod("PASSIVE_SET_CHAOS_RESISTANCE", 100.0)
    )

    private val vaalPact = keystone(
        "KEYSTONE_VAAL_PACT",
        mod("PASSIVE_SET_LIFE_REGENERATION", 0.0), mod("PASSIVE_MORE_LIFE_LEECH", 100.0)
    )

    private val elementalOverload = keystone(
        "KEYSTONE_ELEMENTAL_OVERLOAD",
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
            angle = -90.0,
            branches = listOf(
                listOf(
                    small("INT_WARD_1", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    small("INT_WARD_2", mod("ADD_ALL_ELEMENTAL_RESISTANCES", 10.0)),
                    small("INT_WARD_3", mod("ADD_ENERGY_SHIELD", 20.0)),
                    notable(
                        "INT_WARD_NOTABLE",
                        mod("ADD_ENERGY_SHIELD", 20.0), mod("INCREASED_ENERGY_SHIELD", 14.0)
                    ),
                    chaosInoculation,
                ),
                listOf(
                    small("INT_ARCANE_1", mod("ADD_INTELLIGENCE", 10.0)),
                    small("INT_ARCANE_2", mod("ADD_INTELLIGENCE", 10.0)),
                    small("INT_ARCANE_3", mod("INCREASED_SPELL_DAMAGE", 8.0)),
                    notable(
                        "INT_ARCANE_NOTABLE",
                        mod("INCREASED_SPELL_DAMAGE", 20.0), mod("INCREASED_CAST_SPEED", 5.0),
                        mod("ADD_INTELLIGENCE", 20.0)
                    ),
                ),
                listOf(
                    small("INT_AETHER_1", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_AETHER_2", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_AETHER_3", mod("INCREASED_MANA_REGENERATION", 15.0)),
                    notable(
                        "INT_AETHER_NOTABLE",
                        mod("ADD_ENERGY_SHIELD", 20.0), mod("ADD_MAXIMUM_MANA", 20.0),
                        mod("ADD_INTELLIGENCE", 20.0)
                    ),
                ),
                listOf(
                    small("INT_SOUL_1", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("INT_SOUL_2", mod("ADD_MAXIMUM_MANA", 20.0)),
                    small("INT_SOUL_3", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    notable(
                        "INT_SOUL_NOTABLE",
                        mod("INCREASED_MAXIMUM_LIFE", 8.0), mod("INCREASED_MAXIMUM_MANA", 12.0)
                    ),
                ),
            )
        ),

        // ---------- Тень: ловкость и интеллект ----------
        ClassArea(
            startCode = "DEX_INT_START",
            angle = -30.0,
            branches = listOf(
                listOf(
                    small("DEX_INT_VENOM_1", mod("ADD_CHAOS_RESISTANCE", 8.0)),
                    small("DEX_INT_VENOM_2", mod("ADD_ENERGY_SHIELD", 20.0)),
                    small("DEX_INT_VENOM_3", mod("ADD_CHAOS_DAMAGE", 6.0)),
                    notable(
                        "DEX_INT_VENOM_NOTABLE",
                        mod("ADD_CHAOS_DAMAGE", 12.0), mod("ADD_CHAOS_RESISTANCE", 17.0)
                    ),
                ),
                listOf(
                    small(
                        "DEX_INT_TRICKERY_1",
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    small("DEX_INT_TRICKERY_2", mod("INCREASED_CRITICAL_STRIKE_CHANCE", 10.0)),
                    small("DEX_INT_TRICKERY_3", mod("ADD_CRITICAL_STRIKE_MULTIPLIER", 10.0)),
                    notable(
                        "DEX_INT_TRICKERY_NOTABLE",
                        mod("INCREASED_CRITICAL_STRIKE_CHANCE", 20.0),
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                ),
                listOf(
                    small("DEX_INT_COORD_1", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    small("DEX_INT_COORD_2", mod("INCREASED_CAST_SPEED", 3.0)),
                    small(
                        "DEX_INT_COORD_3",
                        mod("INCREASED_EVASION_AND_ENERGY_SHIELD", 8.0, 8.0)
                    ),
                    notable(
                        "DEX_INT_COORD_NOTABLE",
                        mod("INCREASED_ATTACK_SPEED", 10.0), mod("INCREASED_CAST_SPEED", 8.0),
                        mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    acrobatics,
                ),
                listOf(
                    small("DEX_INT_MELDING_1", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_INT_MELDING_2", mod("ADD_ENERGY_SHIELD", 20.0)),
                    small("DEX_INT_MELDING_3", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "DEX_INT_MELDING_NOTABLE",
                        mod("INCREASED_MAXIMUM_LIFE", 7.0), mod("ADD_ENERGY_SHIELD", 20.0)
                    ),
                ),
            )
        ),

        // ---------- Охотница: ловкость ----------
        ClassArea(
            startCode = "DEX_START",
            angle = 30.0,
            branches = listOf(
                listOf(
                    small("DEX_EVADE_1", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_EVADE_2", mod("ADD_EVASION_RATING", 20.0)),
                    small("DEX_EVADE_3", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "DEX_EVADE_NOTABLE",
                        mod("ADD_EVASION_RATING", 100.0), mod("INCREASED_EVASION_RATING", 30.0)
                    ),
                ),
                listOf(
                    small("DEX_SWIFT_1", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_SWIFT_2", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_SWIFT_3", mod("INCREASED_ATTACK_SPEED", 4.0)),
                    notable(
                        "DEX_SWIFT_NOTABLE",
                        mod("INCREASED_ATTACK_SPEED", 8.0), mod("ADD_DEXTERITY", 20.0)
                    ),
                ),
                listOf(
                    small("DEX_VITALITY_1", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("DEX_VITALITY_2", mod("ADD_PHYSICAL_LIFE_LEECH", 0.2)),
                    small("DEX_VITALITY_3", mod("ADD_LIFE_REGENERATION", 2.0)),
                    notable(
                        "DEX_VITALITY_NOTABLE",
                        mod("INCREASED_MAXIMUM_LIFE", 8.0), mod("INCREASED_STUN_THRESHOLD", 20.0)
                    ),
                    vaalPact,
                ),
                listOf(
                    small("DEX_INCLINATION_1", mod("ADD_DEXTERITY", 10.0)),
                    small("DEX_INCLINATION_2", mod("INCREASED_EVASION_RATING", 8.0)),
                    small("DEX_INCLINATION_3", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    notable(
                        "DEX_INCLINATION_NOTABLE",
                        mod("INCREASED_EVASION_RATING", 18.0), mod("ADD_MAXIMUM_LIFE", 12.0),
                        mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("ADD_DEXTERITY", 30.0)
                    ),
                ),
            )
        ),

        // ---------- Дуэлянт: сила и ловкость ----------
        ClassArea(
            startCode = "STR_DEX_START",
            angle = 90.0,
            branches = listOf(
                listOf(
                    small("STR_DEX_GLADIATOR_1", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    small("STR_DEX_GLADIATOR_2", mod("ADD_DEXTERITY", 10.0)),
                    small("STR_DEX_GLADIATOR_3", mod("INCREASED_ATTACK_SPEED", 3.0)),
                    notable(
                        "STR_DEX_GLADIATOR_NOTABLE",
                        mod("INCREASED_ATTACK_SPEED", 10.0), mod("ADD_DEXTERITY", 20.0)
                    ),
                ),
                listOf(
                    small(
                        "STR_DEX_ARENA_1",
                        mod("ADD_STRENGTH_AND_DEXTERITY", 10.0, 10.0)
                    ),
                    small("STR_DEX_ARENA_2", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    small("STR_DEX_ARENA_3", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_DEX_ARENA_NOTABLE",
                        mod("ADD_LIFE_REGENERATION", 2.0), mod("INCREASED_PHYSICAL_DAMAGE", 10.0),
                        mod("ADD_STRENGTH", 20.0)
                    ),
                ),
                listOf(
                    small("STR_DEX_GUARD_1", mod("ADD_BLOCK_CHANCE", 3.0)),
                    small(
                        "STR_DEX_GUARD_2",
                        mod("INCREASED_ARMOUR_AND_EVASION", 12.0, 12.0)
                    ),
                    small("STR_DEX_GUARD_3", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_DEX_GUARD_NOTABLE",
                        mod("INCREASED_ARMOUR_AND_EVASION", 24.0, 24.0), mod("INCREASED_MAXIMUM_LIFE", 8.0)
                    ),
                    ironReflexes,
                ),
                listOf(
                    small("STR_DEX_STEEL_1", mod("ADD_BLOCK_CHANCE", 3.0)),
                    small("STR_DEX_STEEL_2", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_DEX_STEEL_3", mod("ADD_STRENGTH", 10.0)),
                    notable(
                        "STR_DEX_STEEL_NOTABLE",
                        mod("ADD_BLOCK_CHANCE", 8.0), mod("INCREASED_ARMOUR", 15.0)
                    ),
                ),
            )
        ),

        // ---------- Мародёр: сила ----------
        ClassArea(
            startCode = "STR_START",
            angle = 150.0,
            branches = listOf(
                listOf(
                    small("STR_BULWARK_1", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_BULWARK_2", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_BULWARK_3", mod("INCREASED_ARMOUR", 8.0)),
                    notable(
                        "STR_BULWARK_NOTABLE",
                        mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("INCREASED_ARMOUR", 16.0),
                        mod("ADD_MAXIMUM_LIFE", 16.0), mod("ADD_STRENGTH", 30.0)
                    ),
                    unwaveringStance,
                ),
                listOf(
                    small("STR_MIGHT_1", mod("ADD_STRENGTH", 10.0)),
                    small("STR_MIGHT_2", mod("ADD_STRENGTH", 10.0)),
                    small("STR_MIGHT_3", mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
                    notable(
                        "STR_MIGHT_NOTABLE",
                        mod("INCREASED_ATTACK_SPEED", 4.0), mod("ADD_STRENGTH", 20.0),
                        mod("INCREASED_PHYSICAL_DAMAGE", 26.0)
                    ),
                    resoluteTechnique,
                ),
                listOf(
                    small("STR_BLOOD_1", mod("ADD_LIFE_REGENERATION", 2.0)),
                    small("STR_BLOOD_2", mod("ADD_PHYSICAL_LIFE_LEECH", 0.2)),
                    small("STR_BLOOD_3", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    notable(
                        "STR_BLOOD_NOTABLE",
                        mod("ADD_LIFE_REGENERATION", 4.0), mod("INCREASED_STUN_THRESHOLD", 20.0),
                        mod("ADD_STRENGTH", 20.0)
                    ),
                    bloodMagic,
                ),
                listOf(
                    small("STR_CONSTITUTION_1", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_CONSTITUTION_2", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_CONSTITUTION_3", mod("ADD_STRENGTH", 10.0)),
                    notable(
                        "STR_CONSTITUTION_NOTABLE",
                        mod("ADD_MAXIMUM_LIFE", 20.0), mod("INCREASED_MAXIMUM_LIFE", 10.0)
                    ),
                ),
            )
        ),

        // ---------- Храмовник: сила и интеллект ----------
        ClassArea(
            startCode = "STR_INT_START",
            angle = 210.0,
            branches = listOf(
                listOf(
                    small(
                        "STR_INT_DEVOTION_1",
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    small("STR_INT_DEVOTION_2", mod("INCREASED_ARMOUR", 8.0)),
                    small("STR_INT_DEVOTION_3", mod("INCREASED_ENERGY_SHIELD", 8.0)),
                    notable(
                        "STR_INT_DEVOTION_NOTABLE",
                        mod("INCREASED_ARMOUR", 20.0), mod("INCREASED_ENERGY_SHIELD", 10.0),
                        mod("ADD_LIFE_REGENERATION", 2.0),
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                ),
                listOf(
                    small("STR_INT_RETRIBUTION_1", mod("INCREASED_SPELL_DAMAGE", 8.0)),
                    small(
                        "STR_INT_RETRIBUTION_2",
                        mod("INCREASED_ATTACK_AND_CAST_SPEED", 3.0, 3.0)
                    ),
                    small(
                        "STR_INT_RETRIBUTION_3",
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    notable(
                        "STR_INT_RETRIBUTION_NOTABLE",
                        mod("INCREASED_SPELL_DAMAGE", 15.0),
                        mod("INCREASED_ATTACK_AND_CAST_SPEED", 5.0, 5.0),
                        mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)
                    ),
                    elementalOverload,
                ),
                listOf(
                    small("STR_INT_FAITH_1", mod("INCREASED_MANA_REGENERATION", 20.0)),
                    small("STR_INT_FAITH_2", mod("ADD_MAXIMUM_LIFE", 20.0)),
                    small("STR_INT_FAITH_3", mod("ADD_MAXIMUM_MANA", 20.0)),
                    notable(
                        "STR_INT_FAITH_NOTABLE",
                        mod("INCREASED_MAXIMUM_LIFE", 7.0), mod("INCREASED_MANA_REGENERATION", 20.0),
                        mod("ADD_LIFE_REGENERATION", 2.0)
                    ),
                ),
                listOf(
                    small("STR_INT_ELEMENTS_1", mod("ADD_FIRE_RESISTANCE", 15.0)),
                    small("STR_INT_ELEMENTS_2", mod("ADD_COLD_RESISTANCE", 15.0)),
                    small("STR_INT_ELEMENTS_3", mod("ADD_LIGHTNING_RESISTANCE", 15.0)),
                    notable(
                        "STR_INT_ELEMENTS_NOTABLE",
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
        small("RING_INT_DEX_INT", mod("ADD_INTELLIGENCE", 10.0)),
        small("RING_DEX_INT_DEX", mod("ADD_DEXTERITY", 10.0)),
        small("RING_DEX_STR_DEX", mod("ADD_DEXTERITY", 10.0)),
        small("RING_STR_DEX_STR", mod("ADD_STRENGTH", 10.0)),
        small("RING_STR_STR_INT", mod("ADD_STRENGTH", 10.0)),
        small("RING_STR_INT_INT", mod("ADD_INTELLIGENCE", 10.0)),
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
                    "SCION_HUNTER_1",
                    mod("INCREASED_ATTACK_SPEED", 4.0), mod("ADD_DEXTERITY", 5.0)
                ),
                small(
                    "SCION_HUNTER_2",
                    mod("ADD_EVASION_RATING", 20.0), mod("ADD_MAXIMUM_LIFE", 14.0)
                ),
                notable(
                    "SCION_HUNTER_NOTABLE",
                    mod("INCREASED_PHYSICAL_DAMAGE", 16.0), mod("ADD_DEXTERITY", 20.0)
                ),
            )
        ),
        ScionBranch(
            ringIndex = 3,
            nodes = listOf(
                small(
                    "SCION_WARRIOR_1",
                    mod("ADD_MAXIMUM_LIFE", 12.0), mod("ADD_STRENGTH", 5.0)
                ),
                small(
                    "SCION_WARRIOR_2",
                    mod("ADD_ARMOUR", 20.0), mod("ADD_MAXIMUM_LIFE", 16.0)
                ),
                notable(
                    "SCION_WARRIOR_NOTABLE",
                    mod("ADD_ARMOUR", 50.0), mod("ADD_STRENGTH", 20.0),
                    mod("INCREASED_PHYSICAL_DAMAGE", 16.0)
                ),
            )
        ),
        ScionBranch(
            ringIndex = 5,
            nodes = listOf(
                small(
                    "SCION_SAVANT_1",
                    mod("INCREASED_SPELL_DAMAGE", 10.0), mod("ADD_INTELLIGENCE", 5.0)
                ),
                small(
                    "SCION_SAVANT_2",
                    mod("INCREASED_MANA_REGENERATION", 20.0), mod("ADD_MAXIMUM_LIFE", 14.0)
                ),
                notable(
                    "SCION_SAVANT_NOTABLE",
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
            place(NodeTemplate(area.startCode, START, emptyList()), area.angle, CLASS_RADIUS)

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
        nodes += PlacedNode(NodeTemplate(SCION_START, START, emptyList()), 0, 0)

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
