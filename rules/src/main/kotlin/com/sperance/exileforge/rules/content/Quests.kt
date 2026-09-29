package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/*
 * Задания (1.21.0). Файл `quests.json`: цели, редкости, виды, награды, доска контрактов, сюжет по регионам и
 * задания гильдии. Прогресс - счётчики летописи ([Counter]): каждое `Hero.count` двигает задания героя через
 * [QuestProgress.advance]; выводимые цели ([QuestCounter.DERIVED]) считаются от состояния героя при чтении.
 * Сутки и недели - UTC ([QuestClock]), как вклад гильдии.
 */

/**
 * Вид задания: [DAILY]/[WEEKLY] - личные на сутки и неделю, [CONTRACT] - взятый с доски, [STORY] - шаг сюжета,
 * [GUILD] - личное гильдейское на сутки, [GUILD_DAILY]/[GUILD_WEEKLY] - общие цели всей гильдии.
 */
@Serializable
enum class QuestKind { DAILY, WEEKLY, CONTRACT, STORY, GUILD, GUILD_DAILY, GUILD_WEEKLY }

@Serializable
enum class QuestCategory { COMBAT, CRAFT, ECONOMY, PROGRESS }

/**
 * Условие задания - «аффикс», его число задаёт редкость: [NO_DEATH] - смерть обнуляет прогресс, [ONE_RUN] - начало
 * захода обнуляет прогресс, [HIGH_ZONE] - в зачёт идут только зоны не ниже уровня героя. Подпись - `quest.condition.<код>`.
 */
@Serializable
enum class QuestCondition { NO_DEATH, ONE_RUN, HIGH_ZONE }

/** Где идёт цель боя: где угодно, в одной зоне или в регионе. */
@Serializable
enum class QuestScope { ANY, ZONE, REGION }

/** Выводимые «счётчики» заданий: не копятся, а читаются из героя. */
object QuestCounter {
    const val LEVEL = "LEVEL"
    const val ZONES = "ZONES"
    const val ATLAS = "ATLAS"
    const val TREE = "TREE"
    /** Шаг сюжета «пройти зону»: 1, если зона в пройденных. */
    const val CLEAR = "CLEAR"

    val DERIVED = setOf(LEVEL, ZONES, ATLAS, TREE, CLEAR)
    /** Счётчики боя: только их ограничивают зоны и условия. */
    val COMBAT = setOf(Counter.KILLS, Counter.KILLS_MAGIC, Counter.KILLS_RARE, Counter.BOSSES, Counter.CHESTS, Counter.CRYSTALS, Counter.RUNS)
}

/**
 * Цель: [counter] - счётчик летописи или выводимый, цель = `base + perLevel × уровень` × множители редкости и вида.
 * [conditional] - цель принимает условия (бой). Подпись - `quest.goal.<code>` с `{0}` - числом.
 */
@Serializable
data class QuestGoal(
    val code: String,
    val category: QuestCategory,
    val counter: String,
    val base: Double,
    val perLevel: Double = 0.0,
    val scope: QuestScope = QuestScope.ANY,
    val kinds: Set<QuestKind> = emptySet(),
    val weight: Int = 10,
    val minLevel: Int = 1,
    val conditional: Boolean = false,
    /** Потолок цели: выводимые цели («взять 3 узла») не растут бесконечно. */
    val max: Long = 0,
)

/** Сфера награды: код, сколько и вес среди прочих своей редкости. */
@Serializable
data class QuestOrb(val code: String, val amount: Long = 1, val weight: Int = 1)

/** Редкость задания - как у предметов, без мифической: вес ролла, множители цели и награды, условия, срок контракта, сферы. */
@Serializable
data class QuestRarityRule(
    val rarity: Rarity,
    val weight: Int,
    val target: Double = 1.0,
    val reward: Double = 1.0,
    val conditions: Int = 0,
    val contractHours: Int = 1,
    val orbs: List<QuestOrb> = emptyList(),
)

/** Вид: сколько заданий, какие редкости, множители цели и награды, сколько сфер тянется из таблицы редкости. */
@Serializable
data class QuestKindRule(
    val kind: QuestKind,
    val count: Int = 3,
    val rarities: List<Rarity> = listOf(Rarity.COMMON),
    val target: Double = 1.0,
    val reward: Double = 1.0,
    val orbs: Int = 1,
)

/** Награда: золото `goldBase × уровень^goldPower`, опыт - [experienceShare]% уровня (не больше [experienceCap]%). */
@Serializable
data class QuestRewardRule(val goldBase: Double = 40.0, val goldPower: Double = 1.3, val experienceShare: Double = 5.0, val experienceCap: Double = 100.0)

/** Доска контрактов: листков на доске, активных сразу, новый листок раз в [refillHours]. */
@Serializable
data class QuestBoardRule(val size: Int = 6, val active: Int = 3, val refillHours: Int = 2)

/**
 * Гильдейские: [personal] - личных на сутки; общая цель = `base × участники`, в долю входит тот, кто внёс не меньше
 * [fairShare] средней доли; опыт гильдии - золото награды × [experience].
 */
@Serializable
data class GuildQuestRule(val fairShare: Double = 0.25, val experience: Double = 1.0, val sharedLevel: Int = 0)

/** Шаг сюжета: счётчик (или выводимый [QuestCounter.CLEAR]/[QuestCounter.LEVEL]), сколько, где и редкость. Текст - `quest.story.<code>.name/.description`. */
@Serializable
data class StoryStep(val code: String, val counter: String, val target: Long = 1, val zone: String = "", val rarity: Rarity = Rarity.COMMON)

/** Глава сюжета - регион карты мира; заголовок - `quest.chapter.<region>`. */
@Serializable
data class StoryChapter(val region: String, val steps: List<StoryStep>)

/** Правила заданий - файл `quests.json`. */
@Serializable
data class QuestRules(
    val goals: List<QuestGoal> = emptyList(),
    val rarities: List<QuestRarityRule> = emptyList(),
    val kinds: List<QuestKindRule> = emptyList(),
    val reward: QuestRewardRule = QuestRewardRule(),
    val board: QuestBoardRule = QuestBoardRule(),
    val guild: GuildQuestRule = GuildQuestRule(),
    /** HIGH_ZONE: зона не ниже уровня героя минус столько. */
    val highZoneSlack: Int = 2,
    val story: List<StoryChapter> = emptyList(),
) {
    fun kind(kind: QuestKind): QuestKindRule? = kinds.firstOrNull { it.kind == kind }
    fun rarity(rarity: Rarity): QuestRarityRule? = rarities.firstOrNull { it.rarity == rarity }
    fun goal(code: String): QuestGoal? = goals.firstOrNull { it.code == code }
    val steps: List<StoryStep> get() = story.flatMap { it.steps }

    /** Золото за задание уровня [level] с множителем [scale]. */
    fun gold(level: Int, scale: Double): Long = (reward.goldBase * Math.pow(level.coerceAtLeast(1).toDouble(), reward.goldPower) * scale).toLong().coerceAtLeast(1)

    /** Опыт: доля уровня [level] героя по таблице [classes]; на потолке уровней - ноль. */
    fun experience(classes: ClassesFile, level: Int, scale: Double): Double {
        val from = classes.threshold(level) ?: return 0.0
        val to = classes.nextThreshold(level) ?: return 0.0
        return (to - from) * (reward.experienceShare * scale).coerceAtMost(reward.experienceCap) / 100.0
    }

    fun validate(index: ContentIndex) {
        if (goals.isEmpty() || goals.map { it.code }.toSet().size != goals.size) fail("quests: goals")
        goals.forEach { goal ->
            if (goal.counter !in Counter.ALL && goal.counter !in QuestCounter.DERIVED) fail("quests: ${goal.code} counts unknown ${goal.counter}")
            if (goal.counter in Counter.MAX || goal.counter == QuestCounter.CLEAR) fail("quests: ${goal.code} cannot count ${goal.counter}")
            if (goal.base <= 0.0 || goal.perLevel < 0.0 || goal.weight <= 0 || goal.kinds.isEmpty()) fail("quests: ${goal.code} numbers")
            if (goal.scope != QuestScope.ANY && goal.counter !in QuestCounter.COMBAT) fail("quests: ${goal.code} scope outside combat")
            if (goal.conditional && goal.counter !in QuestCounter.COMBAT) fail("quests: ${goal.code} conditions outside combat")
            if (goal.counter in QuestCounter.DERIVED && (QuestKind.GUILD_DAILY in goal.kinds || QuestKind.GUILD_WEEKLY in goal.kinds)) fail("quests: ${goal.code} shared goals count only tallies")
        }
        if (rarities.isEmpty() || rarities.map { it.rarity }.toSet().size != rarities.size) fail("quests: rarities")
        rarities.forEach { rule ->
            if (rule.rarity == Rarity.MYTHICAL) fail("quests: no mythical quests")
            if (rule.weight <= 0 || rule.target <= 0.0 || rule.reward <= 0.0 || rule.conditions !in 0..QuestCondition.entries.size || rule.contractHours <= 0) fail("quests: rarity ${rule.rarity}")
            if (rule.orbs.isEmpty()) fail("quests: orbs of ${rule.rarity}")
            rule.orbs.forEach { orb ->
                if (orb.code == Orb.MIRROR_OF_KALANDRA.name) fail("quests: the Mirror is never a quest reward")
                if (index.item(orb.code)?.category != Item.CURRENCY || orb.amount <= 0 || orb.weight <= 0) fail("quests: orb ${orb.code} of ${rule.rarity}")
            }
        }
        QuestKind.entries.forEach { kind ->
            val rule = kind(kind) ?: fail("quests: kind $kind")
            if (rule.count < 0 || rule.target <= 0.0 || rule.reward <= 0.0 || rule.orbs < 0) fail("quests: kind $kind numbers")
            rule.rarities.forEach { if (rarity(it) == null) fail("quests: kind $kind names rarity $it without a rule") }
            if (kind != QuestKind.STORY && rule.count > 0) {
                if (rule.rarities.isEmpty()) fail("quests: kind $kind rarities")
                if (goals.none { kind in it.kinds }) fail("quests: kind $kind has no goals")
            }
        }
        if (board.size < 1 || board.active < 1 || board.refillHours < 1) fail("quests: board")
        if (guild.fairShare < 0.0 || guild.experience < 0.0) fail("quests: guild")
        if (reward.goldBase <= 0.0 || reward.goldPower < 0.0 || reward.experienceShare < 0.0 || reward.experienceCap <= 0.0) fail("quests: reward")
        val regions = index.campaign.regions.map { it.code }
        if (story.map { it.region } != regions) fail("quests: story chapters must follow the regions in order")
        val codes = steps.map { it.code }
        if (codes.toSet().size != codes.size) fail("quests: story step codes")
        story.forEach { chapter ->
            if (chapter.steps.isEmpty()) fail("quests: chapter ${chapter.region} is empty")
            val zones = index.campaign.regions.first { it.code == chapter.region }.zones.map { it.code }.toSet()
            chapter.steps.forEach { step ->
                if (step.counter !in Counter.ALL && step.counter !in QuestCounter.DERIVED || step.counter in Counter.MAX) fail("quests: step ${step.code} counts ${step.counter}")
                if (step.target <= 0) fail("quests: step ${step.code} target")
                if (step.zone.isNotEmpty() && step.zone !in zones) fail("quests: step ${step.code} zone ${step.zone} is outside ${chapter.region}")
                if (step.counter == QuestCounter.CLEAR && step.zone.isEmpty()) fail("quests: step ${step.code} clears no zone")
                if (rarity(step.rarity) == null) fail("quests: step ${step.code} rarity")
            }
        }
    }
}

// ==================== СОСТОЯНИЕ ====================

/** Награда задания: выроллена заранее, игрок видит ровно то, что получит. [guildExperience] - только гильдейский. */
@Serializable
data class QuestReward(
    val gold: Long = 0,
    val experience: Double = 0.0,
    val orbs: Map<String, Long> = emptyMap(),
    val guildExperience: Long = 0,
)

/**
 * Задание героя. [goal] - код цели или шага сюжета, [counter] - что его двигает, [start] - значение выводимого
 * счётчика при выдаче, [zones] - где идёт зачёт (пусто - везде), [place] - зона или регион для подписи,
 * [expiresAt] - когда сгорит (0 - со сменой суток или недели).
 */
@Serializable
data class Quest(
    val id: String,
    val kind: QuestKind,
    val goal: String,
    val counter: String,
    val rarity: Rarity,
    val target: Long,
    var progress: Long = 0,
    val start: Long = 0,
    val zones: List<String> = emptyList(),
    val place: String = "",
    val conditions: List<QuestCondition> = emptyList(),
    val reward: QuestReward = QuestReward(),
    val expiresAt: Long = 0,
    var claimed: Boolean = false,
) {
    val done: Boolean get() = progress >= target
    val derived: Boolean get() = counter in QuestCounter.DERIVED
}

/**
 * Гильдейская часть заданий героя: личные задания на сутки ([rolled] - сутки выдачи) и вклад в общие цели -
 * счётчики за сутки и неделю ([day]/[week]), пока герой в гильдии [id]. [claimed] - ключи общих целей, чью долю он забрал.
 */
@Serializable
data class GuildQuestLog(
    val id: String,
    var day: Long = 0,
    var week: Long = 0,
    var dayCounts: MutableMap<String, Long> = mutableMapOf(),
    var weekCounts: MutableMap<String, Long> = mutableMapOf(),
    var rolled: Long = -1,
    var quests: MutableList<Quest> = mutableListOf(),
    var claimed: MutableList<String> = mutableListOf(),
)

/**
 * Задания героя. [day]/[week] - к каким суткам и неделе относятся [daily]/[weekly];
 * [offers] - листки доски, [contracts] - взятые, [refilledAt] - когда доска пополнялась; [chapter]/[step] - где он
 * в сюжете, [story] - текущий шаг.
 */
@Serializable
data class QuestLog(
    var day: Long = -1,
    var week: Long = -1,
    var daily: MutableList<Quest> = mutableListOf(),
    var weekly: MutableList<Quest> = mutableListOf(),
    var offers: MutableList<Quest> = mutableListOf(),
    var contracts: MutableList<Quest> = mutableListOf(),
    var refilledAt: Long = 0,
    var chapter: Int = 0,
    var step: Int = 0,
    var story: Quest? = null,
    var guild: GuildQuestLog? = null,
) {
    /** Личные задания, которые идут прямо сейчас: ежедневные, недельные, контракты, шаг сюжета (листки доски - нет). */
    fun personal(): Sequence<Quest> = sequence {
        yieldAll(daily); yieldAll(weekly); yieldAll(contracts)
        story?.let { yield(it) }
    }

    /** Все задания, которые идут прямо сейчас: личные и гильдейские. */
    fun active(): Sequence<Quest> = personal() + guild?.quests.orEmpty()

    fun find(id: String): Quest? = active().firstOrNull { it.id == id }
}

/** Общая цель гильдии: [key] уникален в пределах суток или недели, цель и сферы закреплены при выдаче. */
@Serializable
data class GuildGoal(
    val key: String,
    val kind: QuestKind,
    val goal: String,
    val counter: String,
    val rarity: Rarity,
    val target: Long,
    val orbs: Map<String, Long> = emptyMap(),
    val guildExperience: Long = 0,
)

/** Общие цели гильдии в её документе: на сутки [day] и неделю [week]; [paid] - ключи, чей опыт гильдия уже получила. */
@Serializable
data class GuildQuestBoard(
    var day: Long = -1,
    var week: Long = -1,
    var daily: MutableList<GuildGoal> = mutableListOf(),
    var weekly: MutableList<GuildGoal> = mutableListOf(),
    var paid: MutableList<String> = mutableListOf(),
)

// ==================== ОТВЕТЫ ====================

/**
 * Доска заданий героя: ежедневные, недельные, листки доски и взятые контракты, шаг сюжета. [dayEndsAt]/[weekEndsAt] -
 * смена суток и недели (мс эпохи), [nextOfferAt] - следующий листок.
 */
@Serializable
data class QuestBoard(
    val daily: List<Quest> = emptyList(),
    val weekly: List<Quest> = emptyList(),
    val offers: List<Quest> = emptyList(),
    val contracts: List<Quest> = emptyList(),
    val story: Quest? = null,
    val chapter: Int = 0,
    val step: Int = 0,
    val dayEndsAt: Long = 0,
    val weekEndsAt: Long = 0,
    val nextOfferAt: Long = 0,
    val activeLimit: Int = 0,
    val money: Long = 0,
)

/** Общая цель глазами участника: весь прогресс, его вклад и порог доли, забрал ли, что получит и вклад каждого по id героя. */
@Serializable
data class GuildGoalView(
    val goal: GuildGoal,
    val progress: Long,
    val mine: Long,
    val need: Long,
    val claimed: Boolean,
    val reward: QuestReward,
    val contributions: Map<String, Long> = emptyMap(),
)

/** Задания гильдии: личные на сутки и общие на сутки и неделю. */
@Serializable
data class GuildQuests(
    val personal: List<Quest> = emptyList(),
    val daily: List<GuildGoalView> = emptyList(),
    val weekly: List<GuildGoalView> = emptyList(),
    val dayEndsAt: Long = 0,
    val weekEndsAt: Long = 0,
    val money: Long = 0,
)

/**
 * Сданное разом задание (1.22.0): [questId] - id личного или ключ общей цели гильдии, [title] - ключ словаря названия
 * ([com.sperance.exileforge.rules.text.LocaleKey.questTitle]), [rewards] - что легло на героя.
 */
@Serializable
data class QuestClaimed(val questId: String, val kind: QuestKind, val title: String, val rewards: QuestReward)

/** Итог «сдать всё» (1.22.0): сданное по порядку, доска после сдачи и золото героя. */
@Serializable
data class QuestClaimAll(val claimed: List<QuestClaimed> = emptyList(), val board: QuestBoard = QuestBoard(), val money: Long = 0)

// ==================== ЛОГИКА ====================

/** Сутки и недели UTC; неделя - с понедельника (1 января 1970 - четверг). */
object QuestClock {
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L

    fun day(now: Long): Long = now / DAY
    fun week(now: Long): Long = (day(now) + 3) / 7
    fun dayEnd(now: Long): Long = (day(now) + 1) * DAY
    fun weekEnd(now: Long): Long = ((week(now) + 1) * 7 - 3) * DAY
}

/** Как счётчик летописи двигает задания героя. */
object QuestProgress {
    /**
     * Счётчик [counter] вырос на [amount] в зоне [zone] (null - вне захода); [guildId] - гильдия героя.
     * Смерть и начало захода обнуляют незавершённые задания с условием; вклад в общие цели гильдии копится за сутки и неделю.
     */
    fun advance(log: QuestLog, counter: String, amount: Long, zone: String?, guildId: String?, now: Long) {
        if (amount <= 0) return
        log.active().forEach { quest ->
            if (quest.claimed || quest.done) return@forEach
            when {
                counter == Counter.DEATHS && QuestCondition.NO_DEATH in quest.conditions -> quest.progress = 0
                counter == Counter.RUNS && QuestCondition.ONE_RUN in quest.conditions -> quest.progress = 0
            }
            if (quest.counter != counter || quest.derived) return@forEach
            if (quest.zones.isNotEmpty() && zone !in quest.zones) return@forEach
            quest.progress = (quest.progress + amount).coerceAtMost(quest.target)
        }
        if (guildId == null) return
        val day = QuestClock.day(now)
        val week = QuestClock.week(now)
        val guild = log.guild?.takeIf { it.id == guildId } ?: GuildQuestLog(guildId, day, week).also { log.guild = it }
        if (guild.day != day) { guild.day = day; guild.dayCounts = mutableMapOf() }
        if (guild.week != week) { guild.week = week; guild.weekCounts = mutableMapOf() }
        guild.dayCounts.merge(counter, amount, Long::plus)
        guild.weekCounts.merge(counter, amount, Long::plus)
    }

    /** Значение выводимого счётчика по состоянию героя. */
    fun derived(counter: String, level: Int, zones: Collection<String>, atlas: Int, tree: Int, zone: String): Long = when (counter) {
        QuestCounter.LEVEL -> level.toLong()
        QuestCounter.ZONES -> zones.size.toLong()
        QuestCounter.ATLAS -> atlas.toLong()
        QuestCounter.TREE -> tree.toLong()
        QuestCounter.CLEAR -> if (zone in zones) 1 else 0
        else -> 0
    }
}
