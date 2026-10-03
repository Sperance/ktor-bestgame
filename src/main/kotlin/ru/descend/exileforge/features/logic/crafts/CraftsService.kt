package ru.descend.exileforge.features.logic.crafts
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.CraftsRules
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Job
import com.sperance.exileforge.rules.content.JobExtra
import com.sperance.exileforge.rules.content.JobInput
import com.sperance.exileforge.rules.content.JobKind
import com.sperance.exileforge.rules.content.JobRecipes
import com.sperance.exileforge.rules.content.MAP_TEMPLATE
import com.sperance.exileforge.rules.content.Profession
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SkillRules
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.SmithChoice
import com.sperance.exileforge.rules.roll.ActiveWork
import com.sperance.exileforge.rules.roll.AffixRoller
import com.sperance.exileforge.rules.roll.AwayStop
import com.sperance.exileforge.rules.roll.CraftsAway
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.ProfessionProgress
import com.sperance.exileforge.rules.roll.Work
import com.sperance.exileforge.rules.roll.WorkBonus
import com.sperance.exileforge.rules.roll.WorkGains
import com.sperance.exileforge.rules.roll.WorkTally
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted
import com.sperance.exileforge.rules.text.LocaleKey
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.exileforge.base.exception.model.ProfessionExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.hero.Stash
import ru.descend.exileforge.features.logic.hero.sheetOf

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
    val kind: JobKind = JobKind.ITEM,
    val inputs: List<JobInput> = emptyList(),
    val band: List<Int> = emptyList(),
    val region: String = "",
    val additives: Boolean = false,
    val open: Boolean = true,
    /** Вариант работы с выбором (1.43.0): что выбрано; у самой работы - пусто, варианты - в [options]. */
    val choice: String = "",
    val options: List<JobView> = emptyList(),
)

/** Профессия героя: уровень, опыт, сколько до следующего, инструмент в её слоте, бонусы и работы. */
@Serializable
data class ProfessionView(val code: String, val tool: String, val level: Int, val experience: Double, val next: Double?, val equipped: ItemInstance?, val bonus: WorkBonus, val jobs: List<JobView>)

/** Идущая работа: когда засчитан последний цикл и когда будет следующий (мс эпохи). */
@Serializable
data class WorkView(
    val profession: String,
    val job: String,
    val settledAt: Long,
    val cycleMillis: Long,
    val nextAt: Long,
    val additives: List<String> = emptyList(),
    val seed: Long = 0,
    val cycle: Long = 0,
    val startedAt: Long = 0,
    val totals: WorkTally = WorkTally(),
    val choice: String = "",
)

/**
 * Всё о ремёслах героя одним ответом; [gains] - что добыли циклы, досчитанные этим обращением, [away] - последний
 * досчёт за отлучку не короче пяти минут (1.66.0), тот же, что в части `crafts` снимка героя.
 */
@Serializable
data class CraftsState(
    val now: Long,
    val rules: CraftsRules,
    val professions: List<ProfessionView>,
    val work: WorkView?,
    val gains: WorkGains,
    val additives: Map<String, String> = emptyMap(),
    val maxAdditives: Int = 0,
    val away: CraftsAway? = null,
)

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
    private val recipes get() = JobRecipes(index)

    suspend fun state(heroId: String): CraftsState {
        val hero = heroes.requireHero(heroId, "crafts")
        val gains = settle(hero)
        return view(hero, gains)
    }

    /** Начать работу [jobCode] с выбором [choice]: текущая досчитывается и сменяется, прогресс её профессии остаётся. */
    suspend fun start(heroId: String, jobCode: String, choice: String, additives: List<String>): CraftsState {
        val method = "craftsStart"
        val hero = heroes.requireHero(heroId, method)
        val gains = settle(hero)
        val (profession, job) = recipe(hero, jobCode, choice) ?: throw ProfessionExceptions.funExceptionJobNotFound(method, LocaleKey.jobName(jobCode))
        val progress = hero.professions[profession.code] ?: ProfessionProgress()
        if (progress.level < job.level) throw ProfessionExceptions.funExceptionLevel(method, LocaleKey.jobName(jobCode))
        if (tool(hero, profession) == null) throw ProfessionExceptions.funExceptionNoTool(method, LocaleKey.enumLabel("EnumEquipmentType", profession.tool.name))
        val crafting = file.crafting
        val chosen = additives.filter { it.isNotBlank() }.distinct()
        chosen.firstOrNull { !job.additives || it !in crafting.additives }?.let { throw ProfessionExceptions.funExceptionAdditive(method, LocaleKey.itemName(it)) }
        if (chosen.size > crafting.maxAdditives) throw ProfessionExceptions.funExceptionAdditive(method, LocaleKey.itemName(chosen.last()))
        if (job.kind == JobKind.JEWEL && hero.level < index.rules.loot.jewelHeroLevel) throw ProfessionExceptions.funExceptionHeroLevel(method, index.rules.loot.jewelHeroLevel.toString())
        if (job.kind == JobKind.MAP && charted(hero, job).isEmpty()) throw ProfessionExceptions.funExceptionLocation(method, LocaleKey.regionName(job.region))
        if (Work.perCycle(job, chosen).any { (item, amount) -> (hero.bag[item] ?: 0) < amount }) throw ProfessionExceptions.funExceptionMaterials(method, LocaleKey.jobName(job.code))
        val now = System.currentTimeMillis()
        hero.work = ActiveWork(profession.code, job.code, now, now, chosen, choice = choice)
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
     * всем, что читает или тратит сумку, чтобы добытое не терялось между экранами. Отлучка (1.66.0) - время с
     * последнего чтения героя ([Hero.seenAt], не реже раза в [SEEN_STEP]) или с последнего цикла; не короче
     * [CraftsAway.MIN_MILLIS] - и досчёт ложится в [Hero.craftsAway] для сводки «Пока вас не было».
     */
    suspend fun settle(hero: Hero): WorkGains {
        val method = "craftsSettle"
        val work = hero.work ?: return WorkGains()
        val (profession, job) = recipe(hero, work.job, work.choice) ?: return WorkGains()
        val progress = hero.professions[profession.code] ?: ProfessionProgress()
        val dice = Dice.system()
        // Кости циклов - с потока героя (1.53.0): семя не уходит клиенту, а перезапуск работы продолжает поток, а не катит новый
        val bonus = bonus(hero, profession)
        val now = System.currentTimeMillis()
        val since = maxOf(work.settledAt, hero.seenAt)
        val result = Work.settle(file.rules, job, progress, bonus, work.settledAt, now, hero.rewards.craftSeed(), hero.rewards.crafted, hero.bag, work.additives)
        if (result.settledAt == work.settledAt && !result.gains.starved) {
            if (now - hero.seenAt >= SEEN_STEP) {
                hero.seenAt = now
                heroes.save(hero, method)
            }
            return result.gains
        }
        // Самоцветы - не раньше `loot.jewelHeroLevel`: огранка ниже него ничего не даёт, кузнец тянет другую базу
        val bases = index.forHero(
            when (job.kind) {
                JobKind.EQUIPMENT -> craftBases(job).filter { base -> SmithChoice.of(work.choice)?.fits(base.value) == true }
                JobKind.JEWEL -> index.templatesBySlot[Slot.JEWEL].orEmpty().filter { !it.unique && it.level in job.band[0]..job.band[1] }.map { Weighted(it, 1) }
                else -> emptyList()
            },
            hero.level,
        )
        val zones = if (job.kind == JobKind.MAP) charted(hero, job) else emptyList()
        // Прибавка инструмента к уровню сделанной вещи (1.57.0) - поверх уровня героя, не выше потолка уровня предмета
        val itemLevel = index.rules.loot.craftedItemLevel(hero.level + bonus.itemLevel.toInt().coerceAtLeast(0), result.progress.level, file.rules.maxLevel)
        val made = List(result.gains.made) { craft(job, work.additives, result.progress.level, itemLevel, dice, bases, zones, hero.level, bonus) }.filterNotNull()
        hero.professions[profession.code] = result.progress
        val gains = result.gains.copy(equipment = made)
        hero.rewards.crafted += result.gains.cycles
        hero.work = if (result.gains.starved) null else work.copy(settledAt = result.settledAt, cycles = work.cycles + result.gains.cycles, totals = work.totals + gains)
        // Возврат материалов (1.53.0) мог вернуть весь расход цикла: нулевое списание - не отказ
        gains.spent.forEach { (code, amount) -> if (amount > 0) hero.spend(code, amount, method) }
        gains.items.forEach { (code, amount) -> hero.earn(code, amount) }
        val received = Stash.receive(hero, made, index)
        // Тайник полон (1.74.0): работа встаёт, а не льёт вещи в переполнение и торговцу.
        if (received.overflowed + received.sold > 0) hero.work = null
        hero.seenAt = now
        if (now - since >= CraftsAway.MIN_MILLIS) {
            val stop = when {
                result.gains.starved -> AwayStop.INPUTS
                now > work.settledAt + (file.rules.offlineHours * 3_600_000).toLong() -> AwayStop.CAP
                received.overflowed + received.sold > 0 -> AwayStop.FULL
                else -> null
            }
            val gained = result.gains.items.mapValues { it.value.toInt() } + made.groupingBy { it.template }.eachCount()
            hero.craftsAway = CraftsAway(
                since, now, work.profession, work.job, work.choice, result.gains.cycles,
                gained.filterValues { it > 0 }, result.gains.spent.mapValues { it.value.toInt() }.filterValues { it > 0 },
                result.gains.experience, result.gains.levels, stop,
            )
        }
        hero.count(Counter.CRAFT_CYCLES, result.gains.cycles.toLong())
        hero.stats.add(com.sperance.exileforge.rules.content.Stat.JOB, job.code, result.gains.cycles.toLong())
        // Книга переписчика - тоже сделанная вещь, хоть и ложится в сумку.
        val books = if (job.output.startsWith(SkillRules.BOOK_PREFIX)) result.gains.items[job.output] ?: 0L else 0L
        hero.count(Counter.CRAFT_MADE, made.size + books)
        heroes.save(hero, method)
        return gains
    }

    private fun view(hero: Hero, gains: WorkGains): CraftsState {
        val rules = file.rules
        val equipped = hero.equipped
        val sheet = index.sheetOf(hero).stats
        val professions = file.professions.map { profession ->
            val progress = hero.professions[profession.code] ?: ProfessionProgress()
            val tool = equipped.firstOrNull { it.slot == profession.tool }
            val bonus = bonus(sheet, tool).guilded(hero)
            ProfessionView(
                profession.code,
                profession.tool.name,
                progress.level,
                progress.experience,
                Work.toNext(rules, progress.level),
                tool,
                bonus,
                profession.jobs.sortedBy { it.level }.map { job ->
                    val options = recipes.options(job, hero.heroClass).map { jobView(it.job, progress.level, bonus, choice = it.choice) }
                    jobView(job, progress.level, bonus, open = if (job.kind == JobKind.MAP) charted(hero, job).isNotEmpty() else !job.kind.chosen || options.isNotEmpty(), options = options)
                },
            )
        }
        val work = hero.work?.let { work ->
            professions.firstOrNull { it.code == work.profession }?.jobs?.firstOrNull { it.code == work.job }
                ?.let { job -> job.options.firstOrNull { it.choice == work.choice } ?: job }?.let { job ->
                    WorkView(work.profession, work.job, work.settledAt, job.cycleMillis, work.settledAt + job.cycleMillis, work.additives, 0, work.cycles, work.startedAt, work.totals, work.choice)
                }
        }
        return CraftsState(System.currentTimeMillis(), rules, professions, work, gains, file.crafting.additives, file.crafting.maxAdditives, hero.craftsAway)
    }

    private fun jobView(job: Job, level: Int, bonus: WorkBonus, open: Boolean = true, choice: String = "", options: List<JobView> = emptyList()): JobView {
        val rules = file.rules
        return JobView(
            job.code, job.level, job.seconds, Work.cycleMillis(rules, job, level, bonus), Work.nothingChance(rules, job, bonus),
            job.output, job.experience, job.extra.map { it.copy(chance = Work.findChance(rules, it, level, bonus)) },
            job.kind, job.inputs, job.band, job.region, job.additives, open, choice, options,
        )
    }

    /** Работа [code] для героя: выбранный вариант [choice] или сама работа без выбора; null - такой нет. */
    private fun recipe(hero: Hero, code: String, choice: String): Pair<Profession, Job>? = file.jobs[code]?.let { (profession, job) -> recipes.resolve(job, choice, hero.heroClass)?.let { profession to it } }

    /** Открытые герою зоны региона картографа. */
    private fun charted(hero: Hero, job: Job): List<String> {
        val region = index.campaign.regions.firstOrNull { it.code == job.region } ?: return emptyList()
        val unlocked = index.world.unlocked(hero.campaign.cleared).toSet()
        val floor = if (job.tier > 0) index.campaign.maps.tiers?.fromLevel ?: Int.MAX_VALUE else 0
        return region.zones.filter { it.code in unlocked && it.level >= floor }.map { it.code }
    }

    /** Базы кузнеца в диапазоне уровней работы - один раз на досчёт. */
    private fun craftBases(job: Job): List<Weighted<ItemTemplate>> = index.templatePool(file.crafting.tables).filter { it.value.requiredLevel in job.band[0]..job.band[1] }

    /**
     * Вещь кузнеца, карта картографа или фляга алхимика за удачный цикл: база своего диапазона (или с
     * шансом уникалка), редкость по таблице, ручная работа от примесей и ещё одна с шансом.
     */
    private fun craft(
        job: Job,
        additives: List<String>,
        level: Int,
        itemLevel: Int,
        dice: Dice,
        bases: List<Weighted<ItemTemplate>>,
        zones: List<String>,
        heroLevel: Int,
        bonus: WorkBonus = WorkBonus(),
    ): ItemInstance? {
        val crafting = file.crafting
        return when (job.kind) {
            JobKind.EQUIPMENT -> {
                if (dice.percent(crafting.uniqueChance * (1 + level / 50.0) * (1 + bonus.unique.coerceAtLeast(0.0) / 100))) {
                    // Уникалка по уровню ремесла и дальности уникалок (1.18.0), а не любая из таблицы
                    Tables.draw(index.forHero(index.templatePoolUpTo(crafting.uniques, level + index.rules.loot.uniqueReach), heroLevel), dice)?.let { return factory.create(Hero.newItemId(), it, Rarity.UNIQUE, dice, level = itemLevel) }
                }
                // Примесь выбирает и базу (1.63.1): локальный % брони, уклонения или ЭЩ куётся только на вещь с этой защитой,
                // а если такой в диапазоне нет - мёртвая строка не ставится вовсе.
                val wanted = additives.mapNotNull { crafting.additives[it] }.mapNotNull(index::modifier)
                val fit = bases.filter { base -> wanted.all { affixes.bears(base.value, it) } }
                val base = Tables.draw(fit.ifEmpty { bases }, dice) ?: return null
                val rarity = bonus.raise(Tables.value<Rarity>(index.tables, crafting.smithRarities, dice) ?: Rarity.COMMON, dice)
                val item = factory.create(Hero.newItemId(), base, rarity, dice, level = itemLevel)
                val guaranteed = wanted.filter { affixes.bears(base, it) }.map { it.code }
                val kind = if (base.slot.isWeapon) "weapon" else "armour"
                val random = Tables.draw(
                    index.modifierPool(listOf("handcrafted:smith:$kind") + crafting.modifiers)
                        .filter { it.value.code !in guaranteed && affixes.bears(base, it.value) },
                    dice,
                )?.code
                    ?.takeIf { dice.percent(crafting.handcraftedChance) }
                val handcrafted = (guaranteed + listOfNotNull(random)).distinct().take(crafting.maxHandcrafted)
                item.also { it.rolls = it.rolls + handcrafted.mapNotNull { code -> affixes.rollCode(code, itemLevel, dice) } }
            }

            JobKind.JEWEL -> {
                val base = Tables.draw(bases, dice) ?: return null
                factory.create(Hero.newItemId(), base, bonus.raise(Tables.value<Rarity>(index.tables, crafting.smithRarities, dice) ?: Rarity.MAGIC, dice), dice, level = itemLevel)
            }

            JobKind.FLASK -> {
                val base = index.template(job.output) ?: return null
                factory.create(Hero.newItemId(), base, bonus.raise(if (dice.percent(crafting.flaskMagicChance)) Rarity.MAGIC else Rarity.COMMON, dice), dice, level = itemLevel)
            }

            JobKind.MAP -> {
                val base = index.template(MAP_TEMPLATE) ?: return null
                val zone = dice.pickOrNull(zones) ?: return null
                val rarity = bonus.raise(Tables.value<Rarity>(index.tables, crafting.mapRarities, dice) ?: Rarity.MAGIC, dice)
                val mapLevel = index.zone(zone)?.level ?: itemLevel
                val item = factory.create(Hero.newItemId(), base, rarity, dice, level = mapLevel).also { it.mapZone = zone }
                if (job.tier > 0) item.mapTier = dice.between(1, job.tier)
                val handcrafted = Tables.draw(index.modifierPool(crafting.mapModifiers), dice)?.code?.takeIf { dice.percent(crafting.mapHandcraftedChance) }
                item.also { it.rolls = it.rolls + listOfNotNull(handcrafted?.let { code -> affixes.rollCode(code, itemLevel, dice) }) }
            }

            JobKind.ITEM, JobKind.BOOK, JobKind.CONDENSE, JobKind.REFINE -> null
        }
    }

    private fun tool(hero: Hero, profession: Profession): ItemInstance? = hero.equipped.firstOrNull { it.slot == profession.tool }

    private fun bonus(hero: Hero, profession: Profession): WorkBonus = bonus(index.sheetOf(hero).stats, tool(hero, profession)).guilded(hero)

    /** Мастерские древа гильдии (1.74.0): прибавка к скорости труда участника. */
    private fun WorkBonus.guilded(hero: Hero): WorkBonus = copy(speed = speed + (hero.guild?.bonuses?.get(com.sperance.exileforge.rules.content.GuildEffect.CRAFT_SPEED) ?: 0.0))

    /** Бонусы труда: ветка дерева из листа героя (инструменты в него не входят) и инструмент своей профессии. */
    private fun bonus(sheet: Map<String, Double>, tool: ItemInstance?): WorkBonus {
        val stats = sheet.toMutableMap()
        tool?.let { SheetCalculator(index).toolOperations(it).forEach { op -> stats.merge(op.stat, op.value, Double::plus) } }
        return WorkBonus.of(stats)
    }

    private companion object {
        /** Как часто чтение без новых циклов отмечает героя увиденным: запись не чаще раза в минуту. */
        const val SEEN_STEP = 60_000L
    }
}
