package features.logic.crafts

import application.enums.EnumRarity
import application.enums.EnumStatStock
import base.exception.model.CharacterExceptions
import base.exception.model.ProfessionExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.logic.locale.LocaleKey
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/** Работа профессии, как её видит герой: базовые числа и те, что дают его уровень и снаряжение. */
@Serializable
data class JobView(
    val code: String,
    val level: Int,
    val seconds: Double,
    val cycleMillis: Long,
    val nothing: Double,
    val output: String,
    val experience: Double,
    val extra: List<JobExtra>,
)

/** Профессия героя: уровень, опыт, сколько до следующего, инструмент в её слоте, бонусы и работы. */
@Serializable
data class ProfessionView(
    val code: String,
    val tool: String,
    val level: Int,
    val experience: Double,
    val next: Double?,
    val equipped: CharacterEquipment?,
    val bonus: WorkBonus,
    val jobs: List<JobView>,
)

/** Идущая работа: когда засчитан последний цикл и когда будет следующий (миллисекунды эпохи). */
@Serializable
data class WorkView(val profession: String, val job: String, val startedAt: Long, val settledAt: Long, val cycleMillis: Long, val nextAt: Long)

/** Всё о ремёслах героя одним ответом; [gains] - что добыли циклы, досчитанные этим обращением. */
@Serializable
data class CraftsState(
    val now: Long,
    val rules: CraftsRules,
    val professions: List<ProfessionView>,
    val work: WorkView?,
    val gains: WorkGains,
)

/**
 * Ремёсла героя (с 0.37.0). Работа идёт на сервере по времени: при каждом обращении - к
 * ремёслам или к сумке - прошедшие циклы досчитываются и добыча сразу ложится в сумку. Одна
 * работа на героя, кампании она не мешает. Без инструмента в слоте профессии работа не
 * начинается; инструмент считается только в своей профессии, дерево - во всех.
 */
class CraftsService : KoinComponent {
    private companion object {
        /** Стартовый набор: самый простой инструмент каждой профессии. */
        const val STARTER = "BRONZE_"
    }

    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val definitions: ModifierDefinitionCache by inject()

    suspend fun state(characterId: String): CraftsState {
        val method = "crafts"
        grantStarter(characterId, method)
        val gains = settle(characterId)
        return view(requireCharacter(characterId, method), gains)
    }

    /** Начать работу: текущая досчитывается и сменяется, прогресс её профессии остаётся. */
    suspend fun start(characterId: String, jobCode: String): CraftsState {
        val method = "craftsStart"
        grantStarter(characterId, method)
        val gains = settle(characterId)
        val character = requireCharacter(characterId, method)
        val (profession, job) = CraftsContent.jobs[jobCode] ?: throw ProfessionExceptions.funExceptionJobNotFound(method, LocaleKey.jobName(jobCode))
        val progress = character.professions[profession.code] ?: ProfessionProgress()
        if (progress.level < job.level) throw ProfessionExceptions.funExceptionLevel(method, LocaleKey.jobName(jobCode))
        if (tool(characterId, profession) == null)
            throw ProfessionExceptions.funExceptionNoTool(method, LocaleKey.enumLabel("EnumEquipmentType", profession.tool.name))
        val now = System.currentTimeMillis()
        character.work = ActiveWork(profession.code, job.code, now, now)
        transactionExecute(method) { session -> characters.update(character, session) }
        return view(character, gains)
    }

    suspend fun stop(characterId: String): CraftsState {
        val method = "craftsStop"
        val gains = settle(characterId)
        val character = requireCharacter(characterId, method)
        if (character.work != null) {
            character.work = null
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return view(character, gains)
    }

    /**
     * Досчитывает работу героя до сейчас и кладёт добычу в сумку. Зовётся перед всем, что
     * читает или тратит сумку, чтобы добытое не терялось между экранами.
     */
    suspend fun settle(characterId: String): WorkGains {
        val method = "craftsSettle"
        val character = characters.findById(characterId) ?: return WorkGains()
        val work = character.work ?: return WorkGains()
        val (profession, job) = CraftsContent.jobs[work.job] ?: return WorkGains()
        val progress = character.professions[profession.code] ?: ProfessionProgress()
        val result = Crafts.settle(CraftsContent.file.rules, job, progress, bonus(characterId, profession), work.settledAt,
            System.currentTimeMillis(), Random.Default)
        if (result.settledAt == work.settledAt) return result.gains
        val stacks = result.gains.items.mapNotNull { (code, amount) ->
            itemsCache.getCache().firstOrNull { it.code == code }?.let { CharacterItems(it._id, amount) }
        }
        character.professions[profession.code] = result.progress
        character.work = work.copy(settledAt = result.settledAt)
        transactionExecute(method) { session ->
            if (stacks.isNotEmpty()) characters.applyItems(character, stacks, method)
            characters.update(character, session)
        }
        return result.gains
    }

    private suspend fun view(character: Character, gains: WorkGains): CraftsState {
        val rules = CraftsContent.file.rules
        val professions = CraftsContent.file.professions.map { profession ->
            val progress = character.professions[profession.code] ?: ProfessionProgress()
            val bonus = bonus(character._id, profession)
            ProfessionView(profession.code, profession.tool.name, progress.level, progress.experience, Crafts.toNext(rules, progress.level),
                tool(character._id, profession), bonus, profession.jobs.sortedBy { it.level }.map { job ->
                    JobView(job.code, job.level, job.seconds, Crafts.cycleMillis(rules, job, progress.level, bonus), Crafts.nothingChance(rules, job, bonus),
                        job.output, job.experience, job.extra.map { it.copy(chance = Crafts.findChance(rules, it, progress.level, bonus)) })
                })
        }
        val work = character.work?.let { work ->
            professions.firstOrNull { it.code == work.profession }?.jobs?.firstOrNull { it.code == work.job }?.let { job ->
                WorkView(work.profession, work.job, work.startedAt, work.settledAt, job.cycleMillis, work.settledAt + job.cycleMillis)
            }
        }
        return CraftsState(System.currentTimeMillis(), rules, professions, work, gains)
    }

    private suspend fun tool(characterId: String, profession: Profession): CharacterEquipment? =
        inventory.findEquipped(characterId).firstOrNull { it.equippedSlot == profession.tool }

    /** Бонусы труда: ветка дерева из листа героя (инструменты в него не входят) и инструмент своей профессии. */
    private suspend fun bonus(characterId: String, profession: Profession): WorkBonus {
        val stats = characters.calculateStats(characterId).stats.toMutableMap()
        tool(characterId, profession)?.params?.forEach { modifier ->
            definitions.findById(modifier.modifierId)?.effects?.forEachIndexed { index, effect ->
                (effect.stat as? EnumStatStock)?.let { stats.merge(it, modifier.values.getOrElse(index) { 0.0 }, Double::plus) }
            }
        }
        fun of(stat: EnumStatStock) = stats[stat] ?: 0.0
        return WorkBonus(of(EnumStatStock.STOCK_WORK_SPEED), of(EnumStatStock.STOCK_WORK_YIELD), of(EnumStatStock.STOCK_WORK_LUCK),
            of(EnumStatStock.STOCK_WORK_EXPERIENCE), of(EnumStatStock.STOCK_WORK_FIND))
    }

    /** Стартовый набор - простой инструмент каждой профессии, надетый в её слот, - один раз на героя. */
    private suspend fun grantStarter(characterId: String, method: String) {
        val character = requireCharacter(characterId, method)
        if (character.toolsGranted) return
        val starters = CraftsContent.file.professions.mapNotNull { profession ->
            equipmentCache.getCache().firstOrNull { it.slot == profession.tool && it.code.startsWith(STARTER) }
        }
        character.toolsGranted = true
        transactionExecute(method) { session ->
            starters.forEach { template ->
                val item = inventory.addRolled(characterId, template, EnumRarity.COMMON, session)
                item.equippedSlot = template.slot
                inventory.update(item, session)
            }
            characters.update(character, session)
        }
    }

    private suspend fun requireCharacter(characterId: String, method: String): Character =
        characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)
}
