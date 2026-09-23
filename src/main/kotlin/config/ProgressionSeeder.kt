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

/**
 * Начальные данные прогрессии: классы персонажей и таблица уровней.
 *
 * Класс задаёт базу, от которой считаются все проценты, точку входа
 * в дерево навыков и конверсии атрибутов. Таблица уровней отвечает
 * и за пороги опыта, и за выдачу очков дерева.
 */
object ProgressionSeeder {

    /**
     * Накопленный опыт для достижения каждого уровня - таблица из Path of Exile.
     * Индекс равен уровню минус один, последний уровень сотый.
     *
     * Числа взяты из игры как есть, а не выведены формулой: в POE эта таблица
     * задана вручную и никакой формулой точно не описывается.
     */
    private val experienceTable = longArrayOf(
        // 1..10
        0L, 525L, 1760L, 3781L, 7184L, 12186L, 19324L, 29377L, 43181L, 61693L,
        // 11..20
        85990L, 117506L, 157384L, 207736L, 269997L, 346462L, 439268L, 551295L, 685171L, 843709L,
        // 21..30
        1030734L, 1249629L, 1504995L, 1800847L, 2142652L, 2535122L, 2984677L, 3496798L, 4080655L, 4742836L,
        // 31..40
        5490247L, 6334393L, 7283446L, 8384398L, 9541110L, 10874351L, 12361842L, 14018289L, 15859432L, 17905634L,
        // 41..50
        20171471L, 22679999L, 25456123L, 28517857L, 31897771L, 35621447L, 39721017L, 44225461L, 49176560L, 54607467L,
        // 51..60
        60565335L, 67094245L, 74247659L, 82075627L, 90631041L, 99984974L, 110197515L, 121340161L, 133497202L, 146749362L,
        // 61..70
        161191120L, 176922628L, 194049893L, 212684946L, 232956711L, 255001620L, 278952403L, 304972236L, 333233648L, 363906163L,
        // 71..80
        397194041L, 433312945L, 472476370L, 514937180L, 560961898L, 610815862L, 664824416L, 723298169L, 786612664L, 855129128L,
        // 81..90
        929261318L, 1009443795L, 1096169525L, 1189918242L, 1291270350L, 1400795257L, 1519130326L, 1646943474L, 1784977296L, 1934009687L,
        // 91..100
        2094900291L, 2268549086L, 2455921256L, 2658074992L, 2876116901L, 3111280300L, 3364828162L, 3638186694L, 3932818530L, 4250334444L,
    )

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
        val startNodeCode: String,
        val strength: Double,
        val dexterity: Double,
        val intelligence: Double,
    )

    /**
     * Семь классов Path of Exile с их настоящими базовыми атрибутами.
     *
     * Числа взяты из RePoE (`characters.min.json`, поле `base_stats`) - это
     * распакованные данные самой игры. Три чистых класса получают 32 единицы
     * своего атрибута и по 14 остальных, три гибридных - по 23 своих и 14
     * третьего, Скион как универсал имеет ровно по 20 каждого.
     */
    /** Коды классов в порядке сида: по ним сервер ищет портреты. */
    val classCodes: List<String> get() = templates.map { it.code }

    private val templates = listOf(
        ClassTemplate(
            code = "MARAUDER",
            startNodeCode = "STR_START",
            strength = 32.0, dexterity = 14.0, intelligence = 14.0
        ),
        ClassTemplate(
            code = "RANGER",
            startNodeCode = "DEX_START",
            strength = 14.0, dexterity = 32.0, intelligence = 14.0
        ),
        ClassTemplate(
            code = "WITCH",
            startNodeCode = "INT_START",
            strength = 14.0, dexterity = 14.0, intelligence = 32.0
        ),
        ClassTemplate(
            code = "DUELIST",
            startNodeCode = "STR_DEX_START",
            strength = 23.0, dexterity = 23.0, intelligence = 14.0
        ),
        ClassTemplate(
            code = "TEMPLAR",
            startNodeCode = "STR_INT_START",
            strength = 23.0, dexterity = 14.0, intelligence = 23.0
        ),
        ClassTemplate(
            code = "SHADOW",
            startNodeCode = "DEX_INT_START",
            strength = 14.0, dexterity = 23.0, intelligence = 23.0
        ),
        ClassTemplate(
            code = "SCION",
            startNodeCode = "SCION_START",
            strength = 20.0, dexterity = 20.0, intelligence = 20.0
        ),
    )

    /**
     * База, одинаковая для всех классов на первом уровне.
     *
     * Здоровье и мана в POE от класса не зависят, различия дают только
     * атрибуты и их конверсии. Значения из RePoE: life 38, mana 34.
     */
    private val sharedBase = listOf(
        STOCK_HEALTH to 38.0,
        STOCK_MANA to 34.0,
    )

    /**
     * Прирост базы за каждый уровень после первого - как в POE.
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
                startNodeCode = template.startNodeCode,
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
     * Очки дерева выдаются по одному за каждый уровень после первого и ещё
     * по одному сверху на каждом десятом - итого 109 за всю прокачку.
     * В POE за уровни дают ровно 99 очков, а оставшиеся 22 приходят с квестов;
     * квестов в проекте нет, их и заменяет бонус за круглые уровни.
     */
    fun seedLevels(): List<ExperienceLevel> = experienceTable.mapIndexed { index, experience ->
        val level = index + 1
        ExperienceLevel(
            level = level,
            experience = experience.toDouble(),
            skillPoints = if (level == 1) 0 else if (level % 10 == 0) 2 else 1,
            _id = "LEVEL_$level".toStableObjectId()
        )
    }
}
