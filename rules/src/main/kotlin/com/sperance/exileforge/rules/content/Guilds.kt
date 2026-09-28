package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import com.sperance.exileforge.rules.sheet.SourceKind
import com.sperance.exileforge.rules.sheet.StatOperation
import com.sperance.exileforge.rules.sheet.StatSource
import kotlinx.serialization.Serializable
import kotlin.math.ceil

/*
 * Гильдии (1.20.0). Файл `guilds.json`: цена основания, состав по уровню, опыт уровней, ранги по вкладу,
 * покровители со строками бонуса, гербы, чат. Покровитель выбирается при основании навсегда; его строки
 * растут с уровнем гильдии и рангом участника и ложатся в лист героя источником [SourceKind.GUILD].
 */

/** Роль в гильдии: глава - всё, офицер - состав, участник - вклад и выход. */
@Serializable
enum class GuildRole { LEADER, OFFICER, MEMBER }

/** Как в гильдию попадают: сразу, по заявке или только по приглашению (приглашения работают всегда). */
@Serializable
enum class GuildMode { OPEN, APPLY, INVITE }

/** Запись журнала гильдии; подпись - ключ `guild.log.<вид>` с `{0}` - героем и `{1}` - подробностью. */
@Serializable
enum class GuildLogKind { CREATED, JOINED, LEFT, KICKED, CONTRIBUTED, PROMOTED, DEMOTED, RANK_UP, LEVEL_UP, LEADER_CHANGED }

@Serializable
data class GuildCreateRule(val level: Int = 20, val gold: Long = 10_000)

/** Мест в составе: [base] на первом уровне, [perLevel] за каждый следующий, не больше [max]. */
@Serializable
data class GuildMembersRule(val base: Int = 10, val perLevel: Int = 5, val max: Int = 30)

/** Суточный потолок вклада героя - [dailyPerLevel] × уровень героя в золотой стоимости; знак гильдии - за каждые [marksPer] золота. */
@Serializable
data class GuildContributionRule(val dailyPerLevel: Long = 2000, val marksPer: Long = 100)

/** Ранг участника с личного вклада [from] за всё время в гильдии; подпись - `guild.rank.<code>`. */
@Serializable
data class GuildRank(val code: String, val from: Long)

/** Строка покровителя: характеристика героя и операция, [perLevel] - прибавка за уровень гильдии. */
@Serializable
data class GuildLineRule(val stat: String, val op: Op, val perLevel: Double)

/**
 * Покровитель: код (`guild.patron.<code>.name` / `.description`), иконка, строки листа героя и [discount] -
 * процент скидки торговца и сбора аукциона за уровень гильдии (у торговли нет стата листа, поэтому отдельно).
 */
@Serializable
data class GuildPatron(val code: String, val icon: String = "", val lines: List<GuildLineRule> = emptyList(), val discount: Double = 0.0)

@Serializable
data class GuildChatRule(val keep: Int = 100, val length: Int = 200, val cooldownSeconds: Int = 3)

/** Одна строка бонуса гильдии, уже посчитанная для героя. */
@Serializable
data class GuildLine(val stat: String, val op: Op, val value: Double)

/**
 * Бонус гильдии героя - часть снимка `guild`: пустой [patron] - героя в гильдии нет. [rank] - код ранга,
 * [discount] - процент скидки торговца и сбора аукциона.
 */
@Serializable
data class GuildBonus(
    val patron: String = "",
    val level: Int = 0,
    val rank: String = "",
    val lines: List<GuildLine> = emptyList(),
    val discount: Double = 0.0,
) {
    val active: Boolean get() = patron.isNotEmpty()

    /** Строки бонуса операциями листа: [com.sperance.exileforge.rules.sheet.SheetCalculator.calculate] берёт их в `extra`. */
    fun operations(): List<StatOperation> =
        lines.map { StatOperation(it.stat, it.op, it.value, source = StatSource(SourceKind.GUILD, patron)) }

    /** Цена со скидкой гильдии; ненулевая цена остаётся ненулевой. */
    fun discounted(price: Long): Long {
        if (discount <= 0.0 || price <= 0) return price
        return ceil(price * (1.0 - discount / 100.0)).toLong().coerceIn(1L, price)
    }
}

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
    /** Процент к бонусу покровителя за каждый ранг выше первого. */
    val rankBonus: Double = 10.0,
    val patrons: List<GuildPatron> = emptyList(),
    val emblems: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val chat: GuildChatRule = GuildChatRule(),
    /** Длина объявления главы. */
    val announcement: Int = 200,
    /** Длина названия: от и до. */
    val name: List<Int> = listOf(3, 24),
    /** Длина тега: от и до. */
    val tag: List<Int> = listOf(2, 4),
) {
    val maxLevel: Int get() = levels.size

    fun patron(code: String): GuildPatron? = patrons.firstOrNull { it.code == code }

    /** Мест в составе на уровне гильдии [level]. */
    fun capacity(level: Int): Int = (members.base + members.perLevel * (level - 1).coerceAtLeast(0)).coerceAtMost(members.max)

    /** Уровень гильдии с опытом [experience]. */
    fun levelFor(experience: Long): Int = levels.count { it <= experience }.coerceIn(1, maxLevel)

    /** С какого опыта начнётся следующий уровень; 0 - потолок. */
    fun next(level: Int): Long = levels.getOrNull(level) ?: 0

    fun rankIndex(contribution: Long): Int = ranks.indexOfLast { it.from <= contribution }.coerceAtLeast(0)

    fun rankFor(contribution: Long): GuildRank = ranks[rankIndex(contribution)]

    /** Строки покровителя на уровне [level] для ранга [rankIndex]: `perLevel × уровень × (1 + rankBonus% × ранг)`. */
    fun bonusLines(patron: String, level: Int, rankIndex: Int): List<GuildLine> {
        val rule = patron(patron) ?: return emptyList()
        val factor = level * rankFactor(rankIndex)
        return rule.lines.map { GuildLine(it.stat, it.op, tenths(it.perLevel * factor)) }
    }

    fun bonus(patron: String, level: Int, rankIndex: Int): GuildBonus {
        val rule = patron(patron) ?: return GuildBonus()
        val rank = ranks.getOrNull(rankIndex.coerceIn(0, ranks.lastIndex))?.code.orEmpty()
        return GuildBonus(patron, level, rank, bonusLines(patron, level, rankIndex), tenths(rule.discount * level * rankFactor(rankIndex)))
    }

    /** Сколько золотой стоимости герой уровня [heroLevel] вносит за сутки. */
    fun dailyLimit(heroLevel: Int): Long = contribution.dailyPerLevel * heroLevel

    /** Знаков гильдии за вклад стоимостью [value]. */
    fun marksFor(value: Long): Long = if (contribution.marksPer <= 0) 0 else value / contribution.marksPer

    private fun rankFactor(rankIndex: Int): Double = 1.0 + rankBonus / 100.0 * rankIndex.coerceIn(0, ranks.lastIndex)

    fun validate(stats: StatRegistry) {
        if (levels.isEmpty() || levels.first() != 0L || levels.zipWithNext().any { (a, b) -> b <= a }) fail("guilds: levels must start at 0 and grow")
        if (ranks.isEmpty() || ranks.first().from != 0L || ranks.zipWithNext().any { (a, b) -> b.from <= a.from }) fail("guilds: ranks must start at 0 and grow")
        if (ranks.map { it.code }.toSet().size != ranks.size) fail("guilds: duplicate rank codes")
        if (patrons.isEmpty() || patrons.map { it.code }.toSet().size != patrons.size) fail("guilds: patrons")
        patrons.forEach { patron ->
            patron.lines.forEach { line ->
                val def = stats[line.stat] ?: fail("guilds: ${patron.code} names unknown stat ${line.stat}")
                if (def.group != StatGroup.HERO) fail("guilds: ${patron.code} names ${line.stat} outside the hero sheet")
                if (line.perLevel <= 0.0) fail("guilds: ${patron.code} ${line.stat} perLevel")
            }
            if (patron.discount < 0.0 || patron.discount * maxLevel * rankFactor(ranks.lastIndex) >= 100.0) fail("guilds: ${patron.code} discount")
        }
        if (emblems.isEmpty() || emblems.toSet().size != emblems.size) fail("guilds: emblems")
        if (colors.isEmpty() || colors.any { !COLOR.matches(it) }) fail("guilds: colors")
        if (name.size != 2 || name[0] < 1 || name[0] > name[1] || tag.size != 2 || tag[0] < 1 || tag[0] > tag[1]) fail("guilds: name or tag length")
        if (members.base < 1 || members.max < members.base || officers < 0 || create.level < 1 || create.gold < 0) fail("guilds: members or create")
        if (chat.keep < 1 || chat.length < 1 || chat.cooldownSeconds < 0 || announcement < 0) fail("guilds: chat")
    }

    // Не private: плагин сериализации вешает serializer() на companion, и приватный прячет его от ContentLoader
    companion object {
        private val COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}
