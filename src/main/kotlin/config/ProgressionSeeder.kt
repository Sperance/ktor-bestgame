package config

import application.enums.EnumStatStock.STOCK_AGILITY
import application.enums.EnumStatStock.STOCK_HEALTH
import application.enums.EnumStatStock.STOCK_INTELLECT
import application.enums.EnumStatStock.STOCK_MANA
import application.enums.EnumStatStock.STOCK_STRENGTH
import application.enums.IntEnumStat
import base.exception.model.ModifierExceptions
import extensions.toStableObjectId
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.progression.CharacterClass
import features.logic.progression.ExperienceLevel
import features.logic.progression.StatValue
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Начальные данные прогрессии: классы персонажей и таблица уровней.
 *
 * Класс задаёт базу, от которой считаются все проценты, точку входа
 * в дерево навыков и конверсии атрибутов. Таблица уровней отвечает
 * и за пороги опыта, и за выдачу очков дерева.
 */
object ProgressionSeeder {

    /**
     * До какого уровня разворачивается таблица опыта.
     */
    private const val MAX_LEVEL = 100

    /**
     * Очки дерева, которые персонаж имеет с самого начала.
     */
    private const val START_SKILL_POINTS = 2

    /**
     * Конверсии, общие для всех классов - как в POE, где атрибуты дают
     * одно и то же независимо от того, кем ты начал.
     */
    private val sharedConversions = listOf(
        "CONVERT_STRENGTH_TO_LIFE",
        "CONVERT_DEXTERITY_TO_EVASION",
        "CONVERT_INTELLIGENCE_TO_MANA",
        "CONVERT_INTELLIGENCE_TO_ENERGY_SHIELD",
        "CONVERT_STRENGTH_TO_PHYSICAL_DAMAGE",
    )

    private data class ClassTemplate(
        val code: String,
        val name: String,
        val startNodeCode: String,
        val description: String,
        val strength: Double,
        val dexterity: Double,
        val intelligence: Double,
    )

    private val templates = listOf(
        ClassTemplate(
            code = "MARAUDER",
            name = "Marauder",
            startNodeCode = "STR_START",
            description = "Сила: больше здоровья и физического урона",
            strength = 32.0, dexterity = 14.0, intelligence = 14.0
        ),
        ClassTemplate(
            code = "RANGER",
            name = "Ranger",
            startNodeCode = "DEX_START",
            description = "Ловкость: уклонение и скорость",
            strength = 14.0, dexterity = 32.0, intelligence = 14.0
        ),
        ClassTemplate(
            code = "WITCH",
            name = "Witch",
            startNodeCode = "INT_START",
            description = "Интеллект: мана, энергощит и заклинания",
            strength = 14.0, dexterity = 14.0, intelligence = 32.0
        ),
    )

    /**
     * База, одинаковая для всех классов на первом уровне.
     */
    private val sharedBase = listOf(
        STOCK_HEALTH to 50.0,
        STOCK_MANA to 40.0,
    )

    /**
     * Прирост базы за каждый уровень после первого.
     */
    private val sharedGrowth = listOf(
        STOCK_HEALTH to 12.0,
        STOCK_MANA to 6.0,
    )

    /**
     * Документы коллекции `CharacterClass`.
     *
     * @param definitions описания модификаторов - из них берутся _id конверсий
     */
    fun seedClasses(definitions: List<ModifierDefinition>): List<CharacterClass> {
        val byCode = definitions.associateBy { it.code }

        fun conversion(code: String): Modifier {
            val definition = byCode[code] ?: throw ModifierExceptions.funExceptionCodeNotFound("seedClasses", code)
            // Значение конверсии фиксировано её описанием, множитель всегда один
            return Modifier.passive(definition._id, definition.effects.map { 1.0 })
        }

        return templates.map { template ->
            CharacterClass(
                code = template.code,
                name = template.name,
                startNodeCode = template.startNodeCode,
                description = template.description,
                baseStats = buildBase(template),
                perLevelStats = sharedGrowth.mapTo(mutableListOf()) { StatValue(it.first, it.second) },
                params = sharedConversions.mapTo(mutableListOf()) { conversion(it) },
                // Справочник пересевается на каждом старте, персонажи ссылаются на _id
                _id = template.code.toStableObjectId()
            )
        }
    }

    private fun buildBase(template: ClassTemplate): MutableList<StatValue> {
        val base = mutableListOf<StatValue>()
        base.add(StatValue(STOCK_STRENGTH as IntEnumStat, template.strength))
        base.add(StatValue(STOCK_AGILITY, template.dexterity))
        base.add(StatValue(STOCK_INTELLECT, template.intelligence))
        sharedBase.forEach { base.add(StatValue(it.first, it.second)) }
        return base
    }

    /**
     * Документы коллекции `ExperienceLevel`.
     *
     * Кривая опыта степенная: первые уровни берутся быстро, последние долго.
     */
    fun seedLevels(): List<ExperienceLevel> = (1..MAX_LEVEL).map { level ->
        ExperienceLevel(
            level = level,
            experience = experienceFor(level),
            skillPoints = if (level == 1) START_SKILL_POINTS else 1,
            _id = "LEVEL_$level".toStableObjectId()
        )
    }

    private fun experienceFor(level: Int): Double =
        ((level - 1).toDouble().pow(2.6) * 100.0).roundToLong().toDouble()
}
