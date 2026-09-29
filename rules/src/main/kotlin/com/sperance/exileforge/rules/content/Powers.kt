package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable
import kotlin.math.floor

/** Силы уникальных предметов (`powers.json`): каждая - свой стат `POWER_*`, что она делает - данными. */
@Serializable
data class PowerBook(val powers: List<Power> = emptyList()) {
    val byStat: Map<String, Power> by lazy { powers.associateBy { it.stat } }
    val sheetPowers: List<Power> by lazy { powers.filter { it.sheet.isNotEmpty() } }
    val worldPowers: List<Power> by lazy { powers.filter { it.world != null } }

    fun validate(stats: StatRegistry) {
        val declared = stats.ofGroup(StatGroup.POWER).mapTo(HashSet()) { it.code }
        val codes = powers.map { it.stat }
        if (codes.toSet().size != codes.size) fail("powers: duplicate powers")
        if (codes.toSet() != declared) fail("powers: powers without a stat or stats without a power: ${(codes.toSet() - declared) + (declared - codes.toSet())}")
        powers.forEach { power ->
            val code = power.stat
            if (power.sheet.isEmpty() && power.on == null && power.world == null) fail("powers: $code does nothing")
            power.sheet.forEach { rule ->
                if (rule.to !in stats || rule.from?.let { it !in stats } == true) fail("powers: $code sheet names an unknown stat")
                if ((rule.op == SheetOp.CONVERT || rule.op == SheetOp.GAIN || rule.op == SheetOp.PER) && rule.from == null) fail("powers: $code ${rule.op} needs a source")
                if (rule.op == SheetOp.PER && rule.per <= 0) fail("powers: $code PER needs a step")
            }
            if (power.on == PowerEvent.EVERY && power.every <= 0) fail("powers: $code EVERY needs a period")
            if (power.on == PowerEvent.STANDING && power.effects.any { it.act != PowerAct.BUFF || it.duration != null }) fail("powers: $code standing lays lines only")
            if (power.roll == PowerRoll.CHANCE && power.chance != null) fail("powers: $code rolls its chance and sets it too")
            power.effects.forEach { effect ->
                val rolled = power.roll == PowerRoll.AMOUNT
                effect.lines.forEach { line -> if (line.stat !in stats || (line.value == null && !rolled)) fail("powers: $code line ${line.stat}") }
                if (effect.act in AMOUNTED && effect.amount == null && !rolled) fail("powers: $code ${effect.act} has no amount")
                if (effect.act in TIMED && effect.duration == null && power.roll != PowerRoll.DURATION && power.on != PowerEvent.STANDING) fail("powers: $code ${effect.act} has no duration")
                if (effect.act == PowerAct.AILMENT && effect.ailment == null) fail("powers: $code AILMENT names none")
                if (effect.act == PowerAct.ECHO && (power.on !in HIT_EVENTS || effect.to != PowerTarget.TARGET || (effect.duration ?: 1.0) <= 0))
                    fail("powers: $code ECHO repeats the hero's own hit on its foe, after a delay")
                if (effect.act == PowerAct.RETALIATE && (power.on !in TAKEN_EVENTS || effect.to != PowerTarget.TARGET)) fail("powers: $code RETALIATE answers a hit taken")
                if (power.on == PowerEvent.STAGE_CLEAR && effect.act in MOMENT_ACTS) fail("powers: $code ${effect.act} needs a blow, a stage clear has none")
                effect.lines.forEach { line -> if (line.scale == PowerScale.MOMENTUM && line.cap <= 0) fail("powers: $code MOMENTUM needs a cap") }
            }
            power.world?.against?.forEach { rarity -> if (MonsterRarity.of(rarity) == null) fail("powers: $code against $rarity") }
        }
    }

    /**
     * Правила листа: после свода модификаторов, по порядку книги, для каждой силы, чей стат не ноль.
     * Округление то же, что у свода, - тот же проход делает и клиент.
     */
    fun applySheet(stats: MutableMap<String, Double>, trace: ((SheetStep) -> Unit)? = null): MutableMap<String, Double> {
        sheetPowers.forEach { power ->
            val rolled = stats[power.stat] ?: 0.0
            if (rolled == 0.0) return@forEach
            power.sheet.forEach { rule ->
                val source = rule.from?.let { stats[it] } ?: 0.0
                val value = rule.value ?: rolled
                val was = stats[rule.to] ?: 0.0
                stats[rule.to] = tenths(when (rule.op) {
                    SheetOp.CONVERT -> {
                        val moved = source * value.coerceIn(0.0, 100.0) / 100
                        stats[rule.from!!] = tenths(source - moved)
                        trace?.invoke(SheetStep(power.stat, rule.from, tenths(source - moved) - source))
                        (stats[rule.to] ?: 0.0) + moved * rule.factor
                    }
                    SheetOp.GAIN -> (stats[rule.to] ?: 0.0) + source * value / 100 * rule.factor
                    SheetOp.PER -> (stats[rule.to] ?: 0.0) + floor(source / rule.per) * value
                    SheetOp.SET -> value
                    SheetOp.MORE -> (stats[rule.to] ?: 0.0) * (1 + value / 100)
                })
                trace?.invoke(SheetStep(power.stat, rule.to, stats.getValue(rule.to) - was, rule.from))
            }
        }
        return stats
    }

    /** Небоевые силы: сумма роллов вида [kind] на листе, действующих на монстра редкости [rarity]. */
    fun worldBonus(sheet: Map<String, Double>, kind: WorldKind, rarity: MonsterRarity? = null): Double =
        worldPowers.filter { power -> power.world!!.gain == kind && (power.world.against.isEmpty() || rarity?.name in power.world.against) }
            .sumOf { sheet[it.stat] ?: 0.0 }

    companion object {
        private val AMOUNTED = setOf(
            PowerAct.HEAL, PowerAct.HURT, PowerAct.BARRIER, PowerAct.DAMAGE, PowerAct.EXECUTE, PowerAct.CHARGES, PowerAct.COOLDOWNS, PowerAct.ECHO, PowerAct.RETALIATE,
        )
        private val TIMED = setOf(PowerAct.BUFF, PowerAct.HEX, PowerAct.BARRIER, PowerAct.STUN, PowerAct.DELAY, PowerAct.INVULNERABLE, PowerAct.ECHO)
        /** События удара героя: у них есть свой удар и его цель - то, что повторяет [PowerAct.ECHO]. */
        private val HIT_EVENTS = setOf(PowerEvent.HIT, PowerEvent.CRIT, PowerEvent.STUN, PowerEvent.INFLICT)
        /** События принятого удара: у них есть урон и атакующий - то, чем отвечает [PowerAct.RETALIATE]. */
        private val TAKEN_EVENTS = setOf(PowerEvent.HIT_TAKEN, PowerEvent.CRIT_TAKEN)
        /** Действия, которым нужен удар события; у зачистки этапа его нет. */
        private val MOMENT_ACTS = setOf(PowerAct.ECHO, PowerAct.RETALIATE, PowerAct.SPREAD, PowerAct.EXECUTE)
    }
}

@Serializable
data class Power(
    val stat: String, val roll: PowerRoll = PowerRoll.AMOUNT, val sheet: List<SheetRule> = emptyList(), val on: PowerEvent? = null,
    val every: Double = 0.0, val checks: List<PowerCheck> = emptyList(), val chance: Double? = null, val cooldown: Double = 0.0,
    val effects: List<PowerEffect> = emptyList(), val world: WorldGain? = null,
)

@Serializable enum class PowerRoll { AMOUNT, CHANCE, DURATION }
@Serializable data class SheetRule(val op: SheetOp, val from: String? = null, val to: String, val factor: Double = 1.0, val per: Double = 1.0, val value: Double? = null)
/** Шаг правила силы по листу: сила [power] сдвинула [stat] на [delta], взяв от [from], если брала. */
data class SheetStep(val power: String, val stat: String, val delta: Double, val from: String? = null)

@Serializable enum class SheetOp { CONVERT, GAIN, PER, SET, MORE }

/**
 * Событие силы. [STAGE_CLEAR] (1.31.0) - этап поэтапного боя выигран и следующий вот-вот начнётся (глубина Бездны,
 * очередь большой волны, волна автозабега): эффекты силы ложатся в начале следующего этапа, до его [FIGHT_START].
 */
@Serializable
enum class PowerEvent { STANDING, FIGHT_START, EVERY, HIT, CRIT, KILL, HIT_TAKEN, CRIT_TAKEN, BLOCK, EVADE, SKILL_USE, FLASK, LOW_LIFE, SHIELD_BROKEN, INFLICT, AILED, STUN, DEATH, STAGE_CLEAR }

@Serializable data class PowerCheck(val check: PowerCheckKind, val value: Double = 0.0, val word: String? = null)

@Serializable
enum class PowerCheckKind {
    LIFE_BELOW, LIFE_ABOVE, LIFE_FULL, SHIELD_FULL, SHIELD_EMPTY, MANA_BELOW, MANA_ABOVE, FOES_AT_LEAST, FOES_AT_MOST, TARGET_RARE, TARGET_AILED,
    TARGET_AILMENT, TARGET_CURSED, TARGET_LIFE_BELOW, TARGET_LIFE_ABOVE, FIGHT_BEFORE, FIGHT_AFTER, SELF_AILED, SELF_CLEAN, FLASK_RUNNING, BARRIER_UP,
    NTH, SPELL, ATTACK, DAMAGE_TYPE, AILMENT,
}

@Serializable
data class PowerEffect(
    val act: PowerAct, val amount: Double? = null, val lines: List<PowerLine> = emptyList(), val duration: Double? = null, val stacks: Int = 1,
    val of: PowerBase = PowerBase.WEAPON, val type: String? = null, val to: PowerTarget = PowerTarget.TARGET, val ailment: String? = null, val leech: Double = 0.0,
)

/**
 * Что делает эффект силы. С 1.31.0: [ECHO] - удар события повторяется через `duration` секунд на `amount`% своего урона
 * по той же цели, если она жива (с `type` - весь стихией `type`); [RETALIATE] - `amount`% только что принятого урона
 * бьёт всех врагов в ряду атакующего (с `type` - стихией `type`).
 */
@Serializable
enum class PowerAct { BUFF, HEX, HEAL, HURT, BARRIER, DAMAGE, AILMENT, CURSE, EXECUTE, STUN, DELAY, RUSH, CHARGES, COOLDOWNS, NEXT_CRIT, INVULNERABLE, CLEANSE, SPREAD, ECHO, RETALIATE }

@Serializable data class PowerLine(val stat: String, val op: Op = Op.ADD, val value: Double? = null, val scale: PowerScale? = null, val cap: Double = 0.0)

/**
 * Счёт боя, с которым растёт строка. [MOMENTUM] (1.31.0) - удары героя подряд по одной цели: смена цели сбрасывает счёт,
 * между этапами поэтапного боя он сохраняется; строке с ним обязателен потолок `cap`.
 */
@Serializable enum class PowerScale { FOES, MISSING_LIFE, SECONDS, KILLS, CURSED_FOES, AILED_FOES, SHIELD, MANA, CHARGES, MOMENTUM }
@Serializable enum class PowerBase { WEAPON, LIFE, SHIELD, MANA, ARMOUR, EVASION, TAKEN, DEALT, TARGET_LIFE, STRENGTH, AGILITY, INTELLECT }
@Serializable enum class PowerTarget { TARGET, ALL, RANDOM, OTHERS, SELF }
@Serializable data class WorldGain(val gain: WorldKind, val against: List<String> = emptyList())
@Serializable enum class WorldKind { EXPERIENCE, QUANTITY, RARITY, GOLD, UNIQUE, MAP, BOOK }
