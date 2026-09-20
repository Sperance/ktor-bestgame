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

/**
 * Начальные данные коллекции `SkillTreeNode` - дерево навыков в духе POE.
 *
 * Три стартовых узла по основным атрибутам, от каждого расходятся ветки
 * малых узлов, завершающиеся нотаблями; ветки сходятся в центральное кольцо,
 * а на краях висят кейстоуны, которые меняют правила и берут за это плату.
 *
 * Бонусы узлов фиксированы: они ссылаются на описания модификаторов
 * из `ModifierDefinition`, но значения задаёт само дерево, а не тир.
 */
object SkillTreeSeeder {

    /**
     * Бонус узла: код описания модификатора и его значения,
     * по одному на каждый эффект описания.
     */
    private data class NodeBonus(val code: String, val values: List<Double>)

    private fun mod(code: String, vararg values: Double) = NodeBonus(code, values.toList())

    private data class NodeTemplate(
        val code: String,
        val name: String,
        val type: EnumSkillNodeType,
        val x: Int,
        val y: Int,
        val bonuses: List<NodeBonus> = emptyList(),
        val description: String? = null,
        val cost: Int = 1,
    )

    private fun start(code: String, name: String, x: Int, y: Int, description: String) =
        NodeTemplate(code, name, START, x, y, description = description, cost = 0)

    private fun small(code: String, name: String, x: Int, y: Int, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, SMALL, x, y, bonuses.toList())

    private fun notable(code: String, name: String, x: Int, y: Int, description: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, NOTABLE, x, y, bonuses.toList(), description)

    private fun keystone(code: String, name: String, x: Int, y: Int, description: String, vararg bonuses: NodeBonus) =
        NodeTemplate(code, name, KEYSTONE, x, y, bonuses.toList(), description)

    // ==================== Узлы ====================

    private val nodes = listOf(

        // ---------- Сила ----------
        start("STR_START", "Way of the Marauder", 0, -320, "Начало силовой ветки дерева"),

        small("STR_MIGHT_1", "Might", -80, -400, mod("ADD_STRENGTH", 10.0)),
        small("STR_MIGHT_2", "Might", -140, -470, mod("ADD_STRENGTH", 10.0)),
        small("STR_MIGHT_3", "Heavy Blows", -200, -540, mod("INCREASED_PHYSICAL_DAMAGE", 8.0)),
        notable(
            "STR_MIGHT_NOTABLE", "Brute Force", -260, -610,
            "Грубая сила: больше силы и физического урона",
            mod("ADD_STRENGTH", 20.0), mod("INCREASED_PHYSICAL_DAMAGE", 20.0)
        ),

        small("STR_BULWARK_1", "Hardened", 90, -400, mod("ADD_MAXIMUM_LIFE", 20.0)),
        small("STR_BULWARK_2", "Armour Plating", 150, -470, mod("INCREASED_ARMOUR", 8.0)),
        small("STR_BULWARK_3", "Hardened", 210, -540, mod("ADD_MAXIMUM_LIFE", 20.0)),
        notable(
            "STR_BULWARK_NOTABLE", "Unbreakable", 270, -610,
            "Несокрушимый: заметно больше запаса здоровья и брони",
            mod("ADD_MAXIMUM_LIFE", 40.0), mod("INCREASED_ARMOUR", 25.0)
        ),

        small("STR_BLOOD_1", "Thick Skin", 0, -420, mod("ADD_LIFE_REGENERATION", 2.0)),
        small("STR_BLOOD_2", "Blood Siphon", 0, -500, mod("ADD_PHYSICAL_LIFE_LEECH", 0.2)),
        notable(
            "STR_BLOOD_NOTABLE", "Blood Drinker", 0, -580,
            "Кровопийца: вытягивает здоровье из нанесённого урона",
            mod("ADD_PHYSICAL_LIFE_LEECH", 0.4), mod("ADD_LIFE_REGENERATION", 4.0)
        ),

        // ---------- Ловкость ----------
        start("DEX_START", "Way of the Ranger", 300, 180, "Начало ловкой ветки дерева"),

        small("DEX_SWIFT_1", "Fleetness", 400, 240, mod("ADD_DEXTERITY", 10.0)),
        small("DEX_SWIFT_2", "Fleetness", 480, 300, mod("ADD_DEXTERITY", 10.0)),
        small("DEX_SWIFT_3", "Quick Hands", 560, 360, mod("INCREASED_ATTACK_SPEED", 4.0)),
        notable(
            "DEX_SWIFT_NOTABLE", "Blur", 640, 420,
            "Смазанное движение: скорость атаки и ловкость",
            mod("ADD_DEXTERITY", 20.0), mod("INCREASED_ATTACK_SPEED", 8.0)
        ),

        small("DEX_EVADE_1", "Nimbleness", 340, 280, mod("INCREASED_EVASION_RATING", 8.0)),
        small("DEX_EVADE_2", "Nimbleness", 380, 360, mod("INCREASED_EVASION_RATING", 8.0)),
        small("DEX_EVADE_3", "Light Step", 420, 440, mod("ADD_MAXIMUM_LIFE", 20.0)),
        notable(
            "DEX_EVADE_NOTABLE", "Shadow Dance", 460, 520,
            "Танец теней: уклонение и скорость передвижения",
            mod("INCREASED_EVASION_RATING", 25.0), mod("INCREASED_MOVEMENT_SPEED", 3.0)
        ),

        small("DEX_PRECISION_1", "Sharp Eye", 250, 300, mod("INCREASED_CRITICAL_STRIKE_CHANCE", 10.0)),
        small("DEX_PRECISION_2", "Killing Edge", 210, 380, mod("ADD_CRITICAL_STRIKE_MULTIPLIER", 10.0)),
        notable(
            "DEX_PRECISION_NOTABLE", "Deadly Aim", 170, 460,
            "Смертельная точность: шанс и сила критического удара",
            mod("INCREASED_CRITICAL_STRIKE_CHANCE", 25.0), mod("ADD_CRITICAL_STRIKE_MULTIPLIER", 20.0)
        ),

        // ---------- Интеллект ----------
        start("INT_START", "Way of the Witch", -300, 180, "Начало магической ветки дерева"),

        small("INT_ARCANE_1", "Insight", -400, 240, mod("ADD_INTELLIGENCE", 10.0)),
        small("INT_ARCANE_2", "Insight", -480, 300, mod("ADD_INTELLIGENCE", 10.0)),
        small("INT_ARCANE_3", "Spell Craft", -560, 360, mod("INCREASED_SPELL_DAMAGE", 8.0)),
        notable(
            "INT_ARCANE_NOTABLE", "Arcane Focus", -640, 420,
            "Сосредоточение: интеллект и урон заклинаний",
            mod("ADD_INTELLIGENCE", 20.0), mod("INCREASED_SPELL_DAMAGE", 20.0)
        ),

        small("INT_AETHER_1", "Mana Flow", -340, 280, mod("ADD_MAXIMUM_MANA", 20.0)),
        small("INT_AETHER_2", "Mana Flow", -380, 360, mod("ADD_MAXIMUM_MANA", 20.0)),
        small("INT_AETHER_3", "Inner Spring", -420, 440, mod("INCREASED_MANA_REGENERATION", 15.0)),
        notable(
            "INT_AETHER_NOTABLE", "Deep Wellspring", -460, 520,
            "Глубокий источник: запас и восстановление маны",
            mod("ADD_MAXIMUM_MANA", 40.0), mod("INCREASED_MANA_REGENERATION", 40.0)
        ),

        small("INT_WARD_1", "Mental Ward", -250, 300, mod("INCREASED_ENERGY_SHIELD", 8.0)),
        small("INT_WARD_2", "Elemental Ward", -210, 380, mod("ADD_ALL_ELEMENTAL_RESISTANCES", 10.0)),
        notable(
            "INT_WARD_NOTABLE", "Aegis of Thought", -170, 460,
            "Щит разума: энергощит и стихийные сопротивления",
            mod("INCREASED_ENERGY_SHIELD", 25.0), mod("ADD_ALL_ELEMENTAL_RESISTANCES", 15.0)
        ),

        // ---------- Центральное кольцо ----------
        small("RING_STR_DEX", "Warrior's Path", 160, -60, mod("ADD_STRENGTH_AND_DEXTERITY", 10.0, 10.0)),
        small("RING_DEX_INT", "Wanderer's Path", 0, 220, mod("ADD_DEXTERITY_AND_INTELLIGENCE", 10.0, 10.0)),
        small("RING_INT_STR", "Mystic's Path", -160, -60, mod("ADD_STRENGTH_AND_INTELLIGENCE", 10.0, 10.0)),
        notable(
            "RING_CENTER", "Heart of the Tree", 0, 40,
            "Сердце дерева: понемногу всех атрибутов",
            mod("ADD_ALL_ATTRIBUTES", 8.0, 8.0, 8.0)
        ),

        // ---------- Кейстоуны ----------
        keystone(
            "KEYSTONE_RESOLUTE_TECHNIQUE", "Resolute Technique", -330, -680,
            "Непреклонная техника: критических ударов больше не будет, зато физический урон растёт",
            mod("PASSIVE_SET_CRITICAL_STRIKE_CHANCE", 0.0), mod("PASSIVE_MORE_PHYSICAL_DAMAGE", 20.0)
        ),
        keystone(
            "KEYSTONE_UNWAVERING_STANCE", "Unwavering Stance", 340, -680,
            "Непоколебимая стойка: уклоняться нельзя, но оглушить почти невозможно",
            mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_STUN_THRESHOLD", 100.0)
        ),
        keystone(
            "KEYSTONE_BLOOD_MAGIC", "Blood Magic", 0, -660,
            "Кровавая магия: маны нет совсем, запас здоровья вырастает",
            mod("PASSIVE_SET_MAXIMUM_MANA", 0.0), mod("PASSIVE_MORE_MAXIMUM_LIFE", 20.0)
        ),
        keystone(
            "KEYSTONE_ACROBATICS", "Acrobatics", 530, 590,
            "Акробатика: уклонение растёт, броня почти пропадает",
            mod("PASSIVE_MORE_EVASION_RATING", 30.0), mod("PASSIVE_MORE_ARMOUR", -50.0)
        ),
        keystone(
            "KEYSTONE_IRON_REFLEXES", "Iron Reflexes", 300, -60,
            "Железные рефлексы: уклонения нет, зато брони вдвое больше",
            mod("PASSIVE_SET_EVASION_RATING", 0.0), mod("PASSIVE_MORE_ARMOUR", 100.0)
        ),
        keystone(
            "KEYSTONE_CHAOS_INOCULATION", "Chaos Inoculation", -530, 590,
            "Прививка от хаоса: здоровья остаётся единица, но хаос больше не берёт",
            mod("PASSIVE_SET_MAXIMUM_LIFE", 1.0), mod("PASSIVE_SET_CHAOS_RESISTANCE", 100.0)
        ),
    )

    // ==================== Связи ====================

    /**
     * Рёбра дерева. Связь двусторонняя, поэтому каждую пару достаточно объявить один раз.
     */
    private val links = listOf(
        // Сила
        "STR_START" to "STR_MIGHT_1",
        "STR_MIGHT_1" to "STR_MIGHT_2",
        "STR_MIGHT_2" to "STR_MIGHT_3",
        "STR_MIGHT_3" to "STR_MIGHT_NOTABLE",
        "STR_MIGHT_NOTABLE" to "KEYSTONE_RESOLUTE_TECHNIQUE",

        "STR_START" to "STR_BULWARK_1",
        "STR_BULWARK_1" to "STR_BULWARK_2",
        "STR_BULWARK_2" to "STR_BULWARK_3",
        "STR_BULWARK_3" to "STR_BULWARK_NOTABLE",
        "STR_BULWARK_NOTABLE" to "KEYSTONE_UNWAVERING_STANCE",

        "STR_START" to "STR_BLOOD_1",
        "STR_BLOOD_1" to "STR_BLOOD_2",
        "STR_BLOOD_2" to "STR_BLOOD_NOTABLE",
        "STR_BLOOD_NOTABLE" to "KEYSTONE_BLOOD_MAGIC",

        // Ловкость
        "DEX_START" to "DEX_SWIFT_1",
        "DEX_SWIFT_1" to "DEX_SWIFT_2",
        "DEX_SWIFT_2" to "DEX_SWIFT_3",
        "DEX_SWIFT_3" to "DEX_SWIFT_NOTABLE",

        "DEX_START" to "DEX_EVADE_1",
        "DEX_EVADE_1" to "DEX_EVADE_2",
        "DEX_EVADE_2" to "DEX_EVADE_3",
        "DEX_EVADE_3" to "DEX_EVADE_NOTABLE",
        "DEX_EVADE_NOTABLE" to "KEYSTONE_ACROBATICS",
        "DEX_SWIFT_NOTABLE" to "KEYSTONE_ACROBATICS",

        "DEX_START" to "DEX_PRECISION_1",
        "DEX_PRECISION_1" to "DEX_PRECISION_2",
        "DEX_PRECISION_2" to "DEX_PRECISION_NOTABLE",

        // Интеллект
        "INT_START" to "INT_ARCANE_1",
        "INT_ARCANE_1" to "INT_ARCANE_2",
        "INT_ARCANE_2" to "INT_ARCANE_3",
        "INT_ARCANE_3" to "INT_ARCANE_NOTABLE",

        "INT_START" to "INT_AETHER_1",
        "INT_AETHER_1" to "INT_AETHER_2",
        "INT_AETHER_2" to "INT_AETHER_3",
        "INT_AETHER_3" to "INT_AETHER_NOTABLE",
        "INT_AETHER_NOTABLE" to "KEYSTONE_CHAOS_INOCULATION",
        "INT_WARD_NOTABLE" to "KEYSTONE_CHAOS_INOCULATION",

        "INT_START" to "INT_WARD_1",
        "INT_WARD_1" to "INT_WARD_2",
        "INT_WARD_2" to "INT_WARD_NOTABLE",

        // Кольцо: связывает три области между собой
        "STR_START" to "RING_STR_DEX",
        "RING_STR_DEX" to "KEYSTONE_IRON_REFLEXES",
        "KEYSTONE_IRON_REFLEXES" to "DEX_START",
        "DEX_START" to "RING_DEX_INT",
        "RING_DEX_INT" to "INT_START",
        "INT_START" to "RING_INT_STR",
        "RING_INT_STR" to "STR_START",

        "RING_STR_DEX" to "RING_CENTER",
        "RING_DEX_INT" to "RING_CENTER",
        "RING_INT_STR" to "RING_CENTER",
    )

    // ==================== Генерация документов ====================

    /**
     * Документы коллекции `SkillTreeNode`.
     *
     * @param definitions описания модификаторов - из них берутся _id бонусов
     */
    fun seed(definitions: List<ModifierDefinition>): List<SkillTreeNode> {
        val byCode = definitions.associateBy { it.code }
        val connections = buildConnections()

        return nodes.map { template ->
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
                cost = template.cost,
                positionX = template.x,
                positionY = template.y,
                description = template.description,
                // Дерево пересевается на каждом старте: _id должен быть стабильным
                _id = template.code.toStableObjectId()
            )
        }
    }

    /**
     * Рёбра, разложенные по узлам. Каждое ребро попадает в оба конца,
     * чтобы документ дерева читался без обратного поиска.
     */
    private fun buildConnections(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        links.forEach { (from, to) ->
            result.getOrPut(from) { mutableListOf() }.add(to)
            result.getOrPut(to) { mutableListOf() }.add(from)
        }
        return result
    }
}
