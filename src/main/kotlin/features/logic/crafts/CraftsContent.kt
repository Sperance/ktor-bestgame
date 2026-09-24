package features.logic.crafts

import application.enums.EnumEquipmentType
import base.exception.model.ProfessionExceptions
import config.ContentResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Побочная находка работы: предмет и шанс в процентах за удачный цикл. */
@Serializable
data class JobExtra(val item: String, val chance: Double)

/** Сколько предмета уходит за цикл. */
@Serializable
data class JobInput(val item: String, val amount: Long)

/** Что даёт удачный цикл: стопку предмета, вещь кузнеца или карту картографа (с 0.38.0). */
enum class JobKind { ITEM, EQUIPMENT, MAP }

/**
 * Работа профессии (с 0.37.0): что добывается, с какого уровня, за сколько секунд цикл, с каким
 * шансом (в процентах) цикл не приносит ничего и сколько опыта даёт удачный цикл.
 */
@Serializable
data class Job(
    val code: String,
    val level: Int,
    val seconds: Double,
    val nothing: Double,
    val output: String,
    val experience: Double,
    val extra: List<JobExtra> = emptyList(),
    /** Что дают удачные циклы (с 0.38.0); у [JobKind.ITEM] это [output]. */
    val kind: JobKind = JobKind.ITEM,
    /** Что уходит за каждый цикл, удачный или нет (с 0.38.0); не хватило - работа встаёт. */
    val inputs: List<JobInput> = emptyList(),
    /** Кузнец: уровни баз, из которых куётся вещь. */
    val band: List<Int> = emptyList(),
    /** Картограф: локация, чью карту он чертит. */
    val map: String = "",
    /** Принимает ли работа примеси алхимика. */
    val additives: Boolean = false,
)

/**
 * Правила ремесла (с 0.38.0): шанс ручной работы без примесей и их потолок, шанс уникалки
 * кузнеца, веса редкости вещей и карт, какая примесь какой модификатор гарантирует и из чего
 * выбирается случайная ручная работа вещи и карты.
 */
@Serializable
data class CraftingRules(
    val handcraftedChance: Double = 20.0,
    val maxHandcrafted: Int = 3,
    val maxAdditives: Int = 2,
    val uniqueChance: Double = 0.5,
    val smithRarities: Map<application.enums.EnumRarity, Int> = emptyMap(),
    val mapRarities: Map<application.enums.EnumRarity, Int> = emptyMap(),
    val mapHandcraftedChance: Double = 25.0,
    val additives: Map<String, String> = emptyMap(),
    val smithHandcrafted: List<String> = emptyList(),
    val mapHandcrafted: List<String> = emptyList(),
)

/** Профессия: её инструмент - слот экипировки, который она читает, - и работы. */
@Serializable
data class Profession(val code: String, val tool: EnumEquipmentType, val jobs: List<Job>)

/**
 * Общие правила ремёсел. [offlineHours] - сколько часов без обращений засчитывается, дальше
 * работа стоит. Уровень профессии до [maxLevel] сокращает цикл до [levelSpeed] процентов и
 * поднимает шанс находок до [levelFind] процентов; [luckCap] - потолок снижения шанса «ничего».
 * Опыт до следующего уровня - `experienceBase × уровень ^ experiencePower`.
 */
@Serializable
data class CraftsRules(
    val offlineHours: Double,
    val maxLevel: Int,
    val levelSpeed: Double,
    val levelFind: Double,
    val luckCap: Double,
    val experienceBase: Double,
    val experiencePower: Double,
)

@Serializable
data class CraftsFile(val rules: CraftsRules, val professions: List<Profession>, val crafting: CraftingRules = CraftingRules())

/**
 * Профессии (с 0.37.0) - `resources/content/professions.json`, как кампания: правила мира, в базу
 * не пишутся и проверяются при старте. Работа идёт на сервере по времени; клиент только
 * показывает и спрашивает.
 */
object CraftsContent {

    const val FILE = "professions.json"

    private val json = Json { ignoreUnknownKeys = true }

    val file: CraftsFile by lazy { load(ContentResource.read(FILE)) }

    val jobs: Map<String, Pair<Profession, Job>> by lazy {
        file.professions.flatMap { profession -> profession.jobs.map { it.code to (profession to it) } }.toMap()
    }

    fun profession(code: String): Profession? = file.professions.firstOrNull { it.code == code }

    fun load(text: String): CraftsFile = json.decodeFromString(CraftsFile.serializer(), text).also(::validate)

    private fun validate(content: CraftsFile) {
        val method = "CraftsContent"
        fun fail(what: String): Nothing = throw ProfessionExceptions.funExceptionContent(method, what)
        content.rules.let { r ->
            if (r.offlineHours <= 0 || r.maxLevel < 2 || r.levelSpeed !in 0.0..90.0 || r.levelFind < 0 || r.luckCap !in 0.0..100.0
                || r.experienceBase <= 0 || r.experiencePower <= 0) fail("rules")
        }
        if (content.professions.map { it.code }.toSet().size != content.professions.size) fail("profession codes")
        if (content.professions.any { !it.tool.isTool }) fail("tool slots")
        if (content.professions.map { it.tool }.toSet().size != content.professions.size) fail("one tool per profession")
        val jobs = content.professions.flatMap { it.jobs }
        if (jobs.map { it.code }.toSet().size != jobs.size) fail("job codes")
        jobs.forEach { job ->
            if (job.level !in 1..content.rules.maxLevel || job.seconds <= 0 || job.nothing !in 0.0..100.0 || job.experience < 0) fail("job ${job.code}")
            if (job.extra.any { it.chance !in 0.0..100.0 }) fail("extra of ${job.code}")
            if (job.inputs.any { it.amount <= 0 }) fail("inputs of ${job.code}")
            when (job.kind) {
                JobKind.ITEM -> if (job.output.isBlank()) fail("output of ${job.code}")
                JobKind.EQUIPMENT -> if (job.band.size != 2 || job.band[0] > job.band[1]) fail("band of ${job.code}")
                JobKind.MAP -> if (job.map.isBlank()) fail("map of ${job.code}")
            }
        }
        content.professions.forEach { if (it.jobs.none { job -> job.level == 1 }) fail("no first-level work in ${it.code}") }
        content.crafting.let { c ->
            if (c.handcraftedChance !in 0.0..100.0 || c.mapHandcraftedChance !in 0.0..100.0 || c.uniqueChance !in 0.0..100.0) fail("crafting chances")
            if (c.maxHandcrafted < 1 || c.maxAdditives !in 0..c.maxHandcrafted) fail("crafting limits")
        }
    }
}
