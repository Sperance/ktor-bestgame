package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/**
 * Итог одного боя (1.49.0), как его насчитал клиент: урон по типам, полученный и вылеченный, исходы ударов героя и по
 * герою, наложенные состояния, длительность и сильнейший удар. Бой считает клиент - сервер лишь складывает это в
 * статистику героя через [sane]; ни наград, ни заданий оно не двигает.
 */
@Serializable
data class FightTally(
    val dealt: Map<String, Long> = emptyMap(),
    val taken: Long = 0,
    val healed: Long = 0,
    val hits: Int = 0,
    val crits: Int = 0,
    val misses: Int = 0,
    val blocked: Int = 0,
    val evaded: Int = 0,
    val ailments: Int = 0,
    val millis: Long = 0,
    val maxHit: Long = 0,
    val boss: Boolean = false,
    val won: Boolean = true,
    /** Кто нанёс последний удар проигранного боя (1.52.0): код монстра. */
    val killer: String? = null,
) {
    /** Тот же итог в пределах правдоподобия: без отрицательных чисел, чужих типов урона и боёв длиннее часа. */
    fun sane(): FightTally = copy(
        dealt = dealt.filterKeys { it in DAMAGE_TYPES }.mapValues { it.value.coerceIn(0, MAX_DAMAGE) },
        taken = taken.coerceIn(0, MAX_DAMAGE), healed = healed.coerceIn(0, MAX_DAMAGE),
        hits = hits.coerceIn(0, MAX_COUNT), crits = crits.coerceIn(0, MAX_COUNT), misses = misses.coerceIn(0, MAX_COUNT),
        blocked = blocked.coerceIn(0, MAX_COUNT), evaded = evaded.coerceIn(0, MAX_COUNT), ailments = ailments.coerceIn(0, MAX_COUNT),
        millis = millis.coerceIn(0, MAX_MILLIS), maxHit = maxHit.coerceIn(0, MAX_DAMAGE), killer = killer?.take(64),
    )

    companion object {
        val DAMAGE_TYPES = setOf("PHYSICAL", "FIRE", "COLD", "LIGHTNING", "CHAOS")
        const val MAX_DAMAGE = 1_000_000_000_000L
        const val MAX_COUNT = 100_000
        const val MAX_MILLIS = 3_600_000L
    }
}

/**
 * Статистика героя (1.49.0): отдельная коллекция, ключ - строка. Суммы копятся, рекорды держат наибольшее ([MAX])
 * или наименьшее ([MIN]). Разбивки по видам - ключи `<группа>:<код>` групп [GROUPS]: убийства по монстрам, боссы,
 * найденное и потраченное по коду предмета сумки, циклы по работам. Нулевое не хранится и не показывается.
 */
object Stat {
    const val FIGHTS = "FIGHTS"
    const val FIGHTS_WON = "FIGHTS_WON"
    const val FIGHT_SECONDS = "FIGHT_SECONDS"
    const val FIGHT_LONGEST = "FIGHT_LONGEST"
    const val BOSS_FASTEST = "BOSS_FASTEST"
    const val DEALT = "DEALT"
    const val HIT_MAX = "HIT_MAX"
    const val TAKEN = "TAKEN"
    const val HEALED = "HEALED"
    const val HITS = "HITS"
    const val CRITS = "CRITS"
    const val MISSES = "MISSES"
    const val BLOCKED = "BLOCKED"
    const val EVADED = "EVADED"
    const val AILMENTS = "AILMENTS"

    /** Уровень сильнейшего убитого монстра (1.52.0). */
    const val LEVEL_MAX = "LEVEL_MAX"

    const val KILL = "KILL"
    const val BOSS = "BOSS"
    const val FOUND = "FOUND"
    const val SPENT = "SPENT"
    const val JOB = "JOB"

    /**
     * Убийства по редкости монстра (1.52.0) не хранятся: это счётчики летописи [Counter.KILLS], [Counter.KILLS_MAGIC],
     * [Counter.KILLS_RARE], [Counter.BOSSES] - группа выводится из них в [fromCounters].
     */
    const val RARITY = "RARITY"

    /** 1.52.0: смерти по убийце, заходы, смерти и сундуки по зонам. */
    const val KILLER = "KILLER"
    const val ZONE_RUNS = "ZONE_RUNS"
    const val ZONE_DEATHS = "ZONE_DEATHS"
    const val ZONE_CHESTS = "ZONE_CHESTS"

    val MAX = setOf(FIGHT_LONGEST, HIT_MAX, LEVEL_MAX)
    val MIN = setOf(BOSS_FASTEST)

    /** Боевые строки летописи по порядку показа; урон по типам - `DEALT:<тип>` сразу под [DEALT]. */
    val COMBAT = listOf(FIGHTS, FIGHTS_WON, FIGHT_SECONDS, FIGHT_LONGEST, BOSS_FASTEST, LEVEL_MAX, DEALT, HIT_MAX, TAKEN, HEALED, HITS, CRITS, MISSES, BLOCKED, EVADED, AILMENTS)
    val GROUPS = listOf(RARITY, KILL, BOSS, KILLER, ZONE_RUNS, ZONE_DEATHS, ZONE_CHESTS, FOUND, SPENT, JOB)

    /** Группы, выводимые из счётчиков летописи: в коллекции статистики не хранятся, старые записи в них не читаются. */
    val DERIVED_GROUPS = setOf(RARITY)

    fun of(group: String, code: String) = "$group:$code"
    fun dealt(type: String) = of(DEALT, type)

    fun derived(key: String): Boolean = key.substringBefore(':', "") in DERIVED_GROUPS

    /** Строки статистики, которые дублировали бы счётчики летописи, - из самих [counters] героя; нулевые опущены. */
    fun fromCounters(counters: Map<String, Long>): Map<String, Long> {
        fun count(code: String) = counters[code] ?: 0L
        val magic = count(Counter.KILLS_MAGIC)
        val rare = count(Counter.KILLS_RARE)
        return mapOf(
            of(RARITY, MonsterRarity.NORMAL.name) to (count(Counter.KILLS) - magic - rare).coerceAtLeast(0),
            of(RARITY, MonsterRarity.MAGIC.name) to magic,
            of(RARITY, MonsterRarity.RARE.name) to rare,
            of(RARITY, MonsterRarity.UNIQUE.name) to count(Counter.BOSSES),
        ).filterValues { it > 0 }
    }
}

/** Прирост статистики за одну команду: суммы и рекорды; пишется одним обновлением после записи героя. */
class StatTally {
    val sums = mutableMapOf<String, Long>()
    val highs = mutableMapOf<String, Long>()
    val lows = mutableMapOf<String, Long>()
    val empty: Boolean get() = sums.isEmpty() && highs.isEmpty() && lows.isEmpty()

    fun add(key: String, amount: Long = 1) {
        if (amount > 0 && !Stat.derived(key)) sums.merge(key, amount, Long::plus)
    }
    fun add(group: String, code: String, amount: Long = 1) = add(Stat.of(group, code), amount)

    fun record(key: String, value: Long) {
        if (value <= 0) return
        if (key in Stat.MIN) lows.merge(key, value, ::minOf) else highs.merge(key, value, ::maxOf)
    }

    /** Итог боя в статистику - уже в пределах правдоподобия. */

    /** Итог боя; [known] - есть ли такой монстр: убийца не из контента не пишется. */
    fun fight(raw: FightTally, known: (String) -> Boolean = { true }) {
        val f = raw.sane()
        if (!f.won) f.killer?.takeIf(known)?.let { add(Stat.KILLER, it) }
        add(Stat.FIGHTS)
        if (f.won) add(Stat.FIGHTS_WON)
        val seconds = f.millis / 1000
        add(Stat.FIGHT_SECONDS, seconds)
        record(Stat.FIGHT_LONGEST, seconds)
        if (f.boss && f.won) record(Stat.BOSS_FASTEST, seconds.coerceAtLeast(1))
        f.dealt.forEach { (type, amount) ->
            add(Stat.DEALT, amount)
            add(Stat.dealt(type), amount)
        }
        record(Stat.HIT_MAX, f.maxHit)
        add(Stat.TAKEN, f.taken)
        add(Stat.HEALED, f.healed)
        add(Stat.HITS, f.hits.toLong())
        add(Stat.CRITS, f.crits.toLong())
        add(Stat.MISSES, f.misses.toLong())
        add(Stat.BLOCKED, f.blocked.toLong())
        add(Stat.EVADED, f.evaded.toLong())
        add(Stat.AILMENTS, f.ailments.toLong())
    }

    fun clear() {
        sums.clear()
        highs.clear()
        lows.clear()
    }
}
