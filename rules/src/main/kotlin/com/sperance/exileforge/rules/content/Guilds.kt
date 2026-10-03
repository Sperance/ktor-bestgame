package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/*
 * Гильдии (1.20.0). Файл `guilds.json`: цена основания, состав по уровню, опыт уровней, ранги по вкладу,
 * фракции, гербы, объявление. Фракция выбирается при основании навсегда (1.25.0) и бонусов не даёт; ранги - только статус.
 */

/** Роль в гильдии: глава - всё, офицер - состав, участник - вклад и выход. */
@Serializable
enum class GuildRole { LEADER, OFFICER, MEMBER }

/** Как в гильдию попадают: сразу, по заявке или только по приглашению (приглашения работают всегда). */
@Serializable
enum class GuildMode { OPEN, APPLY, INVITE }

/** Запись журнала гильдии; подпись - ключ `guild.log.<вид>` с `{0}` - героем и `{1}` - подробностью. */
@Serializable
enum class GuildLogKind { CREATED, JOINED, LEFT, KICKED, CONTRIBUTED, PROMOTED, DEMOTED, RANK_UP, LEVEL_UP, LEADER_CHANGED,
    /** 1.74.0: узел древа взят, древо сброшено, вещь положена в хранилище и взята из него. */
    TREE_NODE, TREE_RESET, STASH_IN, STASH_OUT }

/** Ветви древа гильдии (1.74.0): бой, добыча, хозяйство; подпись - `guild.branch.<код>`. */
@Serializable
enum class GuildBranch { COMBAT, LOOT, ECONOMY }

/**
 * Узел древа гильдии (1.74.0): ветвь, ряд (второй открыт с [GuildTreeRule.rowGate] очков в ветви), до [max] рангов;
 * [effect] - ключ строки, [perRank] - её прибавка за ранг. Подпись - `guild.node.<code>.name` / `.desc`.
 */
@Serializable
data class GuildNode(val code: String, val branch: GuildBranch, val row: Int = 1, val max: Int = 3, val effect: String, val perRank: Double)

/**
 * Древо гильдии: очко за каждый уровень гильдии, распределяет глава; бесплатный сброс раз в [respecDays] дней.
 * Строки боя и добычи ложатся в контекст захода участников ([BrewStat] и [AtlasStat]), хозяйства - в саму гильдию ([GuildEffect]).
 */
@Serializable
data class GuildTreeRule(val nodes: List<GuildNode> = emptyList(), val rowGate: Int = 3, val respecDays: Int = 7, val pointsPerLevel: Int = 1) {
    fun node(code: String): GuildNode? = nodes.firstOrNull { it.code == code }
    fun points(level: Int): Int = level * pointsPerLevel
    fun spent(taken: Map<String, Int>): Int = taken.values.sum()
    fun inBranch(taken: Map<String, Int>, branch: GuildBranch): Int = taken.entries.sumOf { (code, ranks) -> if (node(code)?.branch == branch) ranks else 0 }

    /** Строки взятых узлов по ключам: одинаковые складываются. */
    fun effects(taken: Map<String, Int>): Map<String, Double> =
        taken.mapNotNull { (code, ranks) -> node(code)?.let { it.effect to it.perRank * ranks } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }

    /** Можно ли взять ещё ранг [code] при древе [taken] гильдии уровня [level]. */
    fun canTake(taken: Map<String, Int>, level: Int, code: String): Boolean {
        val node = node(code) ?: return false
        return (taken[code] ?: 0) < node.max && spent(taken) < points(level) && (node.row <= 1 || inBranch(taken, node.branch) >= rowGate)
    }
}

/** Ключи строк хозяйства гильдии (1.74.0): действуют в самой гильдии, не в заходе. */
object GuildEffect {
    const val STASH_TABS = "GUILD_STASH_TABS"
    const val TAKES = "GUILD_TAKES"
    const val DAILY_LIMIT = "GUILD_DAILY_LIMIT"
    const val GROWTH = "GUILD_GROWTH"
    const val CRAFT_SPEED = "GUILD_CRAFT_SPEED"
}

/**
 * Хранилище гильдии (1.74.0): [tabSize] мест во вкладке, [baseTabs] вкладок с основания (хозяйство добавляет), [takesPerDay]
 * взятий в сутки у участника - глава и офицеры без счёта; стопка - одно взятие.
 */
@Serializable
data class GuildStashRule(val tabSize: Int = 50, val baseTabs: Int = 1, val takesPerDay: Int = 10, val maxTabs: Int = 6)

@Serializable
data class GuildCreateRule(val level: Int = 20, val gold: Long = 10_000)

/** Мест в составе: [base] на первом уровне, [perLevel] за каждый следующий, не больше [max]. */
@Serializable
data class GuildMembersRule(val base: Int = 10, val perLevel: Int = 5, val max: Int = 30)

/** Суточный потолок вклада героя - [dailyPerLevel] × уровень героя в золотой стоимости. */
@Serializable
data class GuildContributionRule(val dailyPerLevel: Long = 2000)

/** Ранг участника с личного вклада [from] за всё время в гильдии; подпись - `guild.rank.<code>`. */
@Serializable
data class GuildRank(val code: String, val from: Long)

/** Фракция гильдии: код (`guild.faction.<code>.name` / `.description`), иконка (ключ icons.json) и цвет `#rrggbb`. Бонусов нет. */
@Serializable
data class GuildFaction(val code: String, val icon: String = "", val color: String = "")

/** Правила гильдий - файл `guilds.json`. */
@Serializable
data class GuildRules(
    val create: GuildCreateRule = GuildCreateRule(),
    val members: GuildMembersRule = GuildMembersRule(),
    val officers: Int = 3,
    val leaderIdleDays: Int = 14,
    val rejoinHours: Int = 24,
    val contribution: GuildContributionRule = GuildContributionRule(),
    /** Опыт, с которого начинается уровень 1..N; первый всегда 0. */
    val levels: List<Long> = listOf(0),
    val ranks: List<GuildRank> = listOf(GuildRank("NOVICE", 0)),
    val factions: List<GuildFaction> = emptyList(),
    val emblems: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    /** Длина объявления главы. */
    val announcement: Int = 200,
    /** Длина названия: от и до. */
    val name: List<Int> = listOf(3, 24),
    /** Длина тега: от и до. */
    val tag: List<Int> = listOf(2, 4),
    val tree: GuildTreeRule = GuildTreeRule(),
    val stash: GuildStashRule = GuildStashRule(),
) {
    val maxLevel: Int get() = levels.size

    fun faction(code: String): GuildFaction? = factions.firstOrNull { it.code == code }

    /** Мест в составе на уровне гильдии [level]. */
    fun capacity(level: Int): Int = (members.base + members.perLevel * (level - 1).coerceAtLeast(0)).coerceAtMost(members.max)

    /** Уровень гильдии с опытом [experience]. */
    fun levelFor(experience: Long): Int = levels.count { it <= experience }.coerceIn(1, maxLevel)

    /** С какого опыта начнётся следующий уровень; 0 - потолок. */
    fun next(level: Int): Long = levels.getOrNull(level) ?: 0

    fun rankIndex(contribution: Long): Int = ranks.indexOfLast { it.from <= contribution }.coerceAtLeast(0)

    fun rankFor(contribution: Long): GuildRank = ranks[rankIndex(contribution)]

    /** Сколько золотой стоимости герой уровня [heroLevel] вносит за сутки. */
    fun dailyLimit(heroLevel: Int): Long = contribution.dailyPerLevel * heroLevel

    fun validate() {
        if (levels.isEmpty() || levels.first() != 0L || levels.zipWithNext().any { (a, b) -> b <= a }) fail("guilds: levels must start at 0 and grow")
        if (ranks.isEmpty() || ranks.first().from != 0L || ranks.zipWithNext().any { (a, b) -> b.from <= a.from }) fail("guilds: ranks must start at 0 and grow")
        if (ranks.map { it.code }.toSet().size != ranks.size) fail("guilds: duplicate rank codes")
        if (factions.isEmpty() || factions.map { it.code }.toSet().size != factions.size) fail("guilds: factions")
        factions.firstOrNull { !COLOR.matches(it.color) }?.let { fail("guilds: ${it.code} color") }
        if (emblems.isEmpty() || emblems.toSet().size != emblems.size) fail("guilds: emblems")
        if (colors.isEmpty() || colors.any { !COLOR.matches(it) }) fail("guilds: colors")
        if (name.size != 2 || name[0] < 1 || name[0] > name[1] || tag.size != 2 || tag[0] < 1 || tag[0] > tag[1]) fail("guilds: name or tag length")
        if (members.base < 1 || members.max < members.base || officers < 0 || create.level < 1 || create.gold < 0) fail("guilds: members or create")
        if (announcement < 0) fail("guilds: announcement")
    }

    // Не private: плагин сериализации вешает serializer() на companion, и приватный прячет его от ContentLoader
    companion object {
        private val COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}
