package features.logic.crafts

import base.exception.model.ProfessionExceptions
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.CraftsRules
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Job
import com.sperance.exileforge.rules.content.JobExtra
import com.sperance.exileforge.rules.content.JobInput
import com.sperance.exileforge.rules.content.JobKind
import com.sperance.exileforge.rules.content.Profession
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.roll.ActiveWork
import com.sperance.exileforge.rules.roll.AffixRoller
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.LootRoller
import com.sperance.exileforge.rules.roll.ProfessionProgress
import com.sperance.exileforge.rules.roll.Work
import com.sperance.exileforge.rules.roll.WorkBonus
import com.sperance.exileforge.rules.roll.WorkGains
import com.sperance.exileforge.rules.roll.WorkTally
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted
import com.sperance.exileforge.rules.text.LocaleKey
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.hero.Stash
import features.logic.hero.sheetOf
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Работа профессии, как её видит герой: базовые числа и те, что дают его уровень и снаряжение. */
@Serializable
data class JobView(
    val code: String, val level: Int, val seconds: Double, val cycleMillis: Long, val nothing: Double, val output: String, val experience: Double,
    val extra: List<JobExtra>, val kind: JobKind = JobKind.ITEM, val inputs: List<JobInput> = emptyList(), val band: List<Int> = emptyList(),
    val map: String = "", val additives: Boolean = false, val open: Boolean = true,
)

/** Профессия героя: уровень, опыт, сколько до следующего, инструмент в её слоте, бонусы и работы. */
@Serializable
data class ProfessionView(val code: String, val tool: String, val level: Int, val experience: Double, val next: Double?, val equipped: ItemInstance?, val bonus: WorkBonus, val jobs: List<JobView>)

/** Идущая работа: когда засчитан последний цикл и когда будет следующий (мс эпохи). */
@Serializable
data class WorkView(val profession: String, val job: String, val settledAt: Long, val cycleMillis: Long, val nextAt: Long, val additives: List<String> = emptyList(),
                    val seed: Long = 0, val cycle: Long = 0, val startedAt: Long = 0, val totals: WorkTally = WorkTally())

/** Всё о ремёслах героя одним ответом; [gains] - что добыли циклы, досчитанные этим обращением. */
@Serializable
data class CraftsState(val now: Long, val rules: CraftsRules, val professions: List<ProfessionView>, val work: WorkView?, val gains: WorkGains,
                       val additives: Map<String, String> = emptyMap(), val maxAdditives: Int = 0)

/**
 * Ремёсла героя: работа идёт на сервере по времени и досчитывается при каждом обращении, добыча
 * сразу ложится в сумку и тайник. Одна работа на героя; без инструмента в слоте профессии не начинается.
 */
class CraftsService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val file get() = index.professions
    private val factory by lazy { ItemFactory(index) }
    private val affixes by lazy { AffixRoller(index) }

    suspend fun state(heroId: String): CraftsState {
        val hero = heroes.requireHero(heroId, "crafts")
        val gains = settle(hero)
        return view(hero, gains)
    }

    /** Начать работу: текущая досчитывается и сменяется, прогресс её профессии остаётся. */
    suspend fun start(heroId: String, jobCode: String, additives: List<String>): CraftsState {
        val method = "craftsStart"
        val hero = heroes.requireHero(heroId, method)
        val gains = settle(hero)
        val (profession, job) = file.jobs[jobCode] ?: throw ProfessionExceptions.funExceptionJobNotFound(method, LocaleKey.jobName(jobCode))
        val progress = hero.professions[profession.code] ?: ProfessionProgress()
        if (progress.level < job.level) throw ProfessionExceptions.funExceptionLevel(method, LocaleKey.jobName(jobCode))
        if (tool(hero, profession) == null) throw ProfessionExceptions.funExceptionNoTool(method, LocaleKey.enumLabel("EnumEquipmentType", profession.tool.name))
        val crafting = file.crafting
        val chosen = additives.filter { it.isNotBlank() }.distinct()
        chosen.firstOrNull { !job.additives || it !in crafting.additives }?.let { throw ProfessionExceptions.funExceptionAdditive(method, LocaleKey.itemName(it)) }
        if (chosen.size > crafting.maxAdditives) throw ProfessionExceptions.funExceptionAdditive(method, LocaleKey.itemName(chosen.last()))
        if (job.kind == JobKind.MAP && job.map !in index.world.unlocked(hero.campaign.cleared)) throw ProfessionExceptions.funExceptionLocation(method, LocaleKey.mapName(job.map))
        if (Work.perCycle(job, chosen).any { (item, amount) -> (hero.bag[item] ?: 0) < amount }) throw ProfessionExceptions.funExceptionMaterials(method, LocaleKey.jobName(job.code))
        val now = System.currentTimeMillis()
        hero.work = ActiveWork(profession.code, job.code, now, now, chosen, kotlin.random.Random.nextLong())
        return view(heroes.save(hero, method), gains)
    }

    suspend fun stop(heroId: String): CraftsState {
        val method = "craftsStop"
        val hero = heroes.requireHero(heroId, method)
        val gains = settle(hero)
        if (hero.work != null) {
            hero.work = null
            heroes.save(hero, method)
        }
        return view(hero, gains)
    }

    /**
     * Досчитывает работу до сейчас и кладёт добычу герою; изменившийся герой записывается. Зовётся перед
     * всем, что читает или тратит сумку, чтобы добытое не терялось между экранами.
     */
    suspend fun settle(hero: Hero): WorkGains {
        val method = "craftsSettle"
        val work = hero.work ?: return WorkGains()
        val (profession, job) = file.jobs[work.job] ?: return WorkGains()
        val progress = hero.professions[profession.code] ?: ProfessionProgress()
        val dice = Dice.system()
        val result = Work.settle(file.rules, job, progress, bonus(hero, profession), work.settledAt, System.currentTimeMillis(), work.seed, work.cycles, hero.bag, work.additives)
        if (result.settledAt == work.settledAt && !result.gains.starved) return result.gains
        val bases = if (job.kind == JobKind.EQUIPMENT) craftBases(job) else emptyList()
        val books = if (job.kind == JobKind.BOOK) List(result.gains.made) { scribe(job, dice) }.filterNotNull().groupingBy { it }.eachCount().mapValues { it.value.toLong() } else emptyMap()
        val made = if (job.kind == JobKind.BOOK) emptyList() else List(result.gains.made) { craft(job, work.additives, result.progress.level, dice, bases) }.filterNotNull()
        hero.professions[profession.code] = result.progress
        val gains = result.gains.copy(equipment = made, items = result.gains.items + books, made = if (job.kind == JobKind.BOOK) 0 else result.gains.made)
        hero.work = if (result.gains.starved) null else work.copy(settledAt = result.settledAt, cycles = work.cycles + result.gains.cycles, totals = work.totals + gains)
        gains.spent.forEach { (code, amount) -> hero.spend(code, amount, method) }
        gains.items.forEach { (code, amount) -> hero.earn(code, amount, index.rules.maxStack) }
        Stash.receive(hero, made, index)
        hero.count(Counter.CRAFT_CYCLES, result.gains.cycles.toLong())
        hero.count(Counter.CRAFT_MADE, (made.size + books.values.sum()).toLong())
        heroes.save(hero, method)
        return gains
    }

    private fun view(hero: Hero, gains: WorkGains): CraftsState {
        val rules = file.rules
        val unlocked = index.world.unlocked(hero.campaign.cleared).toSet()
        val equipped = hero.equipped
        val sheet = index.sheetOf(hero).stats
        val professions = file.professions.map { profession ->
            val progress = hero.professions[profession.code] ?: ProfessionProgress()
            val tool = equipped.firstOrNull { it.slot == profession.tool }
            val bonus = bonus(sheet, tool)
            ProfessionView(profession.code, profession.tool.name, progress.level, progress.experience, Work.toNext(rules, progress.level), tool, bonus,
                profession.jobs.sortedBy { it.level }.map { job ->
                    JobView(job.code, job.level, job.seconds, Work.cycleMillis(rules, job, progress.level, bonus), Work.nothingChance(rules, job, bonus),
                        job.output, job.experience, job.extra.map { it.copy(chance = Work.findChance(rules, it, progress.level, bonus)) },
                        job.kind, job.inputs, job.band, job.map, job.additives, job.kind != JobKind.MAP || job.map in unlocked)
                })
        }
        val work = hero.work?.let { work ->
            professions.firstOrNull { it.code == work.profession }?.jobs?.firstOrNull { it.code == work.job }?.let { job ->
                WorkView(work.profession, work.job, work.settledAt, job.cycleMillis, work.settledAt + job.cycleMillis, work.additives, work.seed, work.cycles, work.startedAt, work.totals)
            }
        }
        return CraftsState(System.currentTimeMillis(), rules, professions, work, gains, file.crafting.additives, file.crafting.maxAdditives)
    }

    /** Базы кузнеца в диапазоне уровней работы - один раз на досчёт. */
    private fun craftBases(job: Job): List<Weighted<ItemTemplate>> =
        index.templatePool(file.crafting.tables).filter { it.value.requiredLevel in job.band[0]..job.band[1] }

    /**
     * Вещь кузнеца, карта картографа или фляга алхимика за удачный цикл: база своего диапазона (или с
     * шансом уникалка), редкость по таблице, ручная работа от примесей и ещё одна с шансом.
     */
    private fun craft(job: Job, additives: List<String>, level: Int, dice: Dice, bases: List<Weighted<ItemTemplate>>): ItemInstance? {
        val crafting = file.crafting
        return when (job.kind) {
            JobKind.EQUIPMENT -> {
                if (dice.percent(crafting.uniqueChance * (1 + level / 50.0))) {
                    Tables.draw(index.templatePool(crafting.uniques), dice)?.let { return factory.create(Hero.newItemId(), it, Rarity.UNIQUE, dice) }
                }
                val base = Tables.draw(bases, dice) ?: return null
                val rarity = Tables.value<Rarity>(index.tables, crafting.smithRarities, dice) ?: Rarity.COMMON
                val item = factory.create(Hero.newItemId(), base, rarity, dice)
                val guaranteed = additives.mapNotNull { crafting.additives[it] }
                val kind = if (base.slot.isWeapon) "weapon" else "armour"
                val random = Tables.draw(index.modifierPool(listOf("handcrafted:smith:$kind") + crafting.modifiers).filter { it.value.code !in guaranteed }, dice)?.code
                    ?.takeIf { dice.percent(crafting.handcraftedChance) }
                val handcrafted = (guaranteed + listOfNotNull(random)).distinct().take(crafting.maxHandcrafted)
                item.also { it.rolls = it.rolls + handcrafted.mapNotNull { code -> affixes.rollCode(code, base.level, dice) } }
            }
            JobKind.FLASK -> {
                val base = index.template(job.output) ?: return null
                factory.create(Hero.newItemId(), base, if (dice.percent(crafting.flaskMagicChance)) Rarity.UNCOMMON else Rarity.COMMON, dice)
            }
            JobKind.MAP -> {
                val base = index.template(LootRoller(index).mapTemplate(job.map)) ?: return null
                val rarity = Tables.value<Rarity>(index.tables, crafting.mapRarities, dice) ?: Rarity.UNCOMMON
                val item = factory.create(Hero.newItemId(), base, rarity, dice)
                val handcrafted = Tables.draw(index.modifierPool(crafting.mapModifiers), dice)?.code?.takeIf { dice.percent(crafting.mapHandcraftedChance) }
                item.also { it.rolls = it.rolls + listOfNotNull(handcrafted?.let { code -> affixes.rollCode(code, base.level, dice) }) }
            }
            JobKind.ITEM, JobKind.BOOK -> null
        }
    }

    /** Книга зачарователя: случайное умение класса работы, открытое не позже её потолка. */
    private fun scribe(job: Job, dice: Dice): String? = dice.pickOrNull(index.skills.ofClass(job.output).filter { it.unlock <= job.band[0] })?.book

    private fun tool(hero: Hero, profession: Profession): ItemInstance? = hero.equipped.firstOrNull { it.slot == profession.tool }

    private fun bonus(hero: Hero, profession: Profession): WorkBonus = bonus(index.sheetOf(hero).stats, tool(hero, profession))

    /** Бонусы труда: ветка дерева из листа героя (инструменты в него не входят) и инструмент своей профессии. */
    private fun bonus(sheet: Map<String, Double>, tool: ItemInstance?): WorkBonus {
        val stats = sheet.toMutableMap()
        tool?.let { SheetCalculator(index).expandRolls(it.rolls).forEach { op -> stats.merge(op.stat, op.value, Double::plus) } }
        return WorkBonus.of(stats)
    }
}
