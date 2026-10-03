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

    /** Силы слотов (1.32.0): зеркало и усиление надетых вещей - их применяет лист до свода. */
    val slotPowers: Map<String, SlotRule> by lazy { powers.mapNotNull { power -> power.slots?.let { power.stat to it } }.toMap() }

    fun validate(stats: StatRegistry) {
        val declared = stats.ofGroup(StatGroup.POWER).mapTo(HashSet()) { it.code }
        val codes = powers.map { it.stat }
        if (codes.toSet().size != codes.size) fail("powers: duplicate powers")
        if (codes.toSet() != declared) fail("powers: powers without a stat or stats without a power: ${(codes.toSet() - declared) + (declared - codes.toSet())}")
        powers.forEach { power ->
            val code = power.stat
            if (power.sheet.isEmpty() && power.on == null && power.world == null && power.slots == null) fail("powers: $code does nothing")
            power.sheet.forEach { rule ->
                if (rule.to !in stats || rule.from?.let { it !in stats } == true) fail("powers: $code sheet names an unknown stat")
                if ((rule.op == SheetOp.CONVERT || rule.op == SheetOp.GAIN || rule.op == SheetOp.PER) && rule.from == null) fail("powers: $code ${rule.op} needs a source")
                if (rule.per <= 0) fail("powers: $code ${rule.op} needs a positive step")
                if (rule.cap < 0 || (rule.cap > 0 && rule.op !in CAPPED)) fail("powers: $code ${rule.op} takes no cap")
                if (rule.under != null && rule.from == null) fail("powers: $code gates on no source")
            }
            power.slots?.let { rule ->
                if (rule.pick == SlotPick.SLOTS && rule.slots.isEmpty()) fail("powers: $code names no slots")
                if (rule.pick != SlotPick.SLOTS && rule.slots.isNotEmpty()) fail("powers: $code picks ${rule.pick} and names slots")
                if (rule.op == SlotOp.MIRROR && rule.pick != SlotPick.OTHER_RING) fail("powers: $code mirrors only the other ring")
                if (rule.slots.any { it.isTool || it.isFlask }) fail("powers: $code reaches a tool or a flask")
                if (rule.value?.let { it < -100 } == true) fail("powers: $code takes more than a whole")
            }
            if (power.on == PowerEvent.EVERY && power.every <= 0) fail("powers: $code EVERY needs a period")
            if (power.on == PowerEvent.STANDING && power.effects.any { it.act != PowerAct.BUFF || it.duration != null }) fail("powers: $code standing lays lines only")
            if (power.roll == PowerRoll.CHANCE && power.chance != null) fail("powers: $code rolls its chance and sets it too")
            (power.effects + power.effects.flatMap { it.options }).forEach { effect ->
                val rolled = power.roll == PowerRoll.AMOUNT
                effect.lines.forEach { line -> if (line.stat !in stats || (line.value == null && !rolled)) fail("powers: $code line ${line.stat}") }
                effect.lines.forEach { line -> if (line.upto != null && (line.value == null || line.upto < line.value)) fail("powers: $code line ${line.stat} spreads from nothing") }
                if ((effect.act == PowerAct.ONE_OF) != effect.options.isNotEmpty()) fail("powers: $code ONE_OF picks among its options, and only it has them")
                if (effect.options.any { it.act == PowerAct.ONE_OF }) fail("powers: $code ONE_OF inside ONE_OF")
                if ((effect.act == PowerAct.CHARGE) != (effect.charge != null)) fail("powers: $code CHARGE names its kind, and only it does")
                effect.charge?.let { kind ->
                    if (effect.consume && kind == ChargeKind.RANDOM) fail("powers: $code consumes a random kind")
                    if (!effect.consume && kind == ChargeKind.ALL) fail("powers: $code gains every kind at once")
                    if (effect.amount?.let { it < 1 } == true) fail("powers: $code CHARGE gains less than one")
                }
                if (effect.consume && effect.act != PowerAct.CHARGE) fail("powers: $code consumes outside CHARGE")
                if (effect.cap < 0 || (effect.cap > 0 && effect.scale == null)) fail("powers: $code effect cap without a scale")
                if (effect.to == PowerTarget.PET && effect.act !in PET_ACTS) fail("powers: $code ${effect.act} cannot reach the pet")
                if (effect.of == PowerBase.PET_LIFE && effect.act != PowerAct.DAMAGE && effect.act != PowerAct.HEAL) fail("powers: $code PET_LIFE is a base of a blow or a heal")
                if (effect.act in AMOUNTED && effect.amount == null && !rolled) fail("powers: $code ${effect.act} has no amount")
                if (effect.act in TIMED && effect.duration == null && power.roll != PowerRoll.DURATION && power.on != PowerEvent.STANDING) fail("powers: $code ${effect.act} has no duration")
                if (effect.act == PowerAct.AILMENT && effect.ailment == null) fail("powers: $code AILMENT names none")
                if (effect.act == PowerAct.ECHO && (power.on !in HIT_EVENTS || effect.to != PowerTarget.TARGET || (effect.duration ?: 1.0) <= 0)) {
                    fail("powers: $code ECHO repeats the hero's own hit on its foe, after a delay")
                }
                if (effect.act == PowerAct.RETALIATE && (power.on !in TAKEN_EVENTS || effect.to != PowerTarget.TARGET)) fail("powers: $code RETALIATE answers a hit taken")
                if (power.on == PowerEvent.STAGE_CLEAR && effect.act in MOMENT_ACTS) fail("powers: $code ${effect.act} needs a blow, a stage clear has none")
                effect.lines.forEach { line -> if (line.scale == PowerScale.MOMENTUM && line.cap <= 0) fail("powers: $code MOMENTUM needs a cap") }
            }
            power.checks.forEach { check ->
                if (check.check == PowerCheckKind.CHARGES_AT_LEAST && (ChargeKind.of(check.word)?.real != true || check.value < 1)) fail("powers: $code CHARGES_AT_LEAST names a kind and a count")
            }
            if (power.on in PET_EVENTS && power.effects.any { it.act in MOMENT_ACTS - PowerAct.EXECUTE }) fail("powers: $code the pet's moment has no hero blow")
            power.world?.against?.forEach { rarity -> if (MonsterRarity.of(rarity) == null) fail("powers: $code against $rarity") }
        }
    }

    /**
     * Правила листа: после свода модификаторов, по порядку книги, для каждой силы, чей стат не ноль.
     * Округление то же, что у свода, - тот же проход делает и клиент. MORE над процент-статом ([percent], 1.32.0) множит весь его
     * множитель `100 + x`, как строка MORE в бою.
     */
    fun applySheet(stats: MutableMap<String, Double>, trace: ((SheetStep) -> Unit)? = null, percent: (String) -> Boolean = { false }): MutableMap<String, Double> {
        sheetPowers.forEach { power ->
            val rolled = stats[power.stat] ?: 0.0
            if (rolled == 0.0) return@forEach
            power.sheet.forEach { rule ->
                val source = rule.from?.let { stats[it] } ?: 0.0
                if (rule.under != null && source >= rule.under) return@forEach
                val value = rule.value ?: rolled
                val was = stats[rule.to] ?: 0.0
                stats[rule.to] = tenths(
                    when (rule.op) {
                        SheetOp.CONVERT -> {
                            val moved = source * value.coerceIn(0.0, 100.0) / 100
                            stats[rule.from!!] = tenths(source - moved)
                            trace?.invoke(SheetStep(power.stat, rule.from, tenths(source - moved) - source))
                            (stats[rule.to] ?: 0.0) + moved * rule.factor
                        }

                        SheetOp.GAIN -> was + rule.capped(source * value / 100 * rule.factor)

                        SheetOp.PER -> was + rule.capped(floor(source / rule.per) * value)

                        SheetOp.SET -> value

                        // С источником (1.32.0) - `value`% за каждые `per` источника, до `cap`%; без него - `value`% разом.
                        SheetOp.MORE -> {
                            val more = 1 + rule.capped(if (rule.from == null || rule.under != null) value else floor(source / rule.per) * value) / 100
                            if (percent(rule.to)) (100 + was) * more - 100 else was * more
                        }

                        SheetOp.ADD -> was + value
                    },
                )
                trace?.invoke(SheetStep(power.stat, rule.to, stats.getValue(rule.to) - was, rule.from))
            }
        }
        return stats
    }

    /** Небоевые силы: сумма роллов вида [kind] на листе, действующих на монстра редкости [rarity]. */
    fun worldBonus(sheet: Map<String, Double>, kind: WorldKind, rarity: MonsterRarity? = null): Double = worldPowers.filter { power -> power.world!!.gain == kind && (power.world.against.isEmpty() || rarity?.name in power.world.against) }
        .sumOf { sheet[it.stat] ?: 0.0 }

    companion object {
        private val AMOUNTED = setOf(
            PowerAct.HEAL, PowerAct.HURT, PowerAct.BARRIER, PowerAct.DAMAGE, PowerAct.EXECUTE, PowerAct.CHARGES, PowerAct.COOLDOWNS, PowerAct.ECHO, PowerAct.RETALIATE,
        )
        private val TIMED = setOf(PowerAct.BUFF, PowerAct.HEX, PowerAct.BARRIER, PowerAct.STUN, PowerAct.DELAY, PowerAct.INVULNERABLE, PowerAct.ECHO)

        /** Правила листа с потолком прибавки (1.32.0). */
        private val CAPPED = setOf(SheetOp.GAIN, SheetOp.PER, SheetOp.MORE)

        /** Что может достаться питомцу (1.32.0): лечение и благо. */
        private val PET_ACTS = setOf(PowerAct.HEAL, PowerAct.BUFF)

        /** События питомца (1.32.0): удар, убийство и гибель боевого питомца героя. */
        private val PET_EVENTS = setOf(PowerEvent.PET_HIT, PowerEvent.PET_KILL, PowerEvent.PET_DEATH)

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
    val stat: String,
    val roll: PowerRoll = PowerRoll.AMOUNT,
    val sheet: List<SheetRule> = emptyList(),
    val on: PowerEvent? = null,
    val every: Double = 0.0,
    val checks: List<PowerCheck> = emptyList(),
    val chance: Double? = null,
    val cooldown: Double = 0.0,
    val effects: List<PowerEffect> = emptyList(),
    val world: WorldGain? = null,
    val slots: SlotRule? = null,
)

@Serializable enum class PowerRoll { AMOUNT, CHANCE, DURATION }

/**
 * Правило листа силы. [cap] (1.32.0) - потолок прибавки GAIN/PER и процента MORE по модулю, 0 - без него; [under] (1.32.0) - правило
 * действует, только пока источник [from] меньше этого (например, «без осквернённых вещей»).
 */
@Serializable
data class SheetRule(
    val op: SheetOp,
    val from: String? = null,
    val to: String,
    val factor: Double = 1.0,
    val per: Double = 1.0,
    val value: Double? = null,
    val cap: Double = 0.0,
    val under: Double? = null,
) {
    fun capped(amount: Double): Double = if (cap > 0) amount.coerceIn(-cap, cap) else amount
}

/** Шаг правила силы по листу: сила [power] сдвинула [stat] на [delta], взяв от [from], если брала. */
data class SheetStep(val power: String, val stat: String, val delta: Double, val from: String? = null)

/**
 * Операция правила листа. [MORE] с источником (1.32.0) множит на `value`% за каждые `per` источника; [ADD] (1.32.0) - прибавка `value`
 * к итогу стата (с [SheetRule.under] - пока источник мал).
 */
@Serializable enum class SheetOp { CONVERT, GAIN, PER, SET, MORE, ADD }

/**
 * Сила слота (1.32.0), применяется листом до свода: [SlotOp.AMPLIFY] множит все строки вещей в выбранных местах на `1 + value/100`
 * (`-100` гасит вещь), [SlotOp.MIRROR] копирует в носителя строки вещи во втором кольце на `value`%. `value` - своё или ролл силы.
 * [pick]: [SlotPick.SLOTS] - вещи в [slots] (кроме носителя), [SlotPick.OTHER_RING] - кольцо во втором кольцевом месте, [SlotPick.SELF] - сам носитель.
 * [rarity] - только вещи этой редкости; [ifOtherUnique] - только если во втором кольце уникалка. Доля считается от ролла силы на вещи,
 * а не от усиленного, поэтому силы слотов друг друга не разгоняют; строки сил слотов не множатся и не копируются.
 */
@Serializable
data class SlotRule(
    val op: SlotOp,
    val pick: SlotPick = SlotPick.SLOTS,
    val slots: List<Slot> = emptyList(),
    val rarity: Rarity? = null,
    val ifOtherUnique: Boolean = false,
    val value: Double? = null,
)

@Serializable enum class SlotOp { AMPLIFY, MIRROR }

@Serializable enum class SlotPick { SLOTS, OTHER_RING, SELF }

/**
 * Вид заряда (1.32.0): ярость, сила, выносливость; [RANDOM] - один из трёх наугад (только получение), [ALL] - все виды (только трата).
 * Бонусы заряда, база максимума и срок - в `rules.json` ([ChargeRules]).
 */
@Serializable
enum class ChargeKind {
    FRENZY,
    POWER,
    ENDURANCE,
    RANDOM,
    ALL,
    ;

    val real: Boolean get() = this == FRENZY || this == POWER || this == ENDURANCE

    companion object {
        val REAL = listOf(FRENZY, POWER, ENDURANCE)
        fun of(name: String?): ChargeKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Событие силы. [STAGE_CLEAR] (1.31.0) - этап поэтапного боя выигран и следующий вот-вот начнётся (глубина Бездны,
 * очередь большой волны, волна автозабега): эффекты силы ложатся в начале следующего этапа, до его [FIGHT_START].
 */
@Serializable
enum class PowerEvent {
    STANDING,
    FIGHT_START,
    EVERY,
    HIT,
    CRIT,
    KILL,
    HIT_TAKEN,
    CRIT_TAKEN,
    BLOCK,
    EVADE,
    SKILL_USE,
    FLASK,
    LOW_LIFE,
    SHIELD_BROKEN,
    INFLICT,
    AILED,
    STUN,
    DEATH,
    STAGE_CLEAR,

    /** Боевой питомец героя (1.32.0): его удар попал (урон момента - его), он убил врага, он пал. */
    PET_HIT,
    PET_KILL,
    PET_DEATH,
}

@Serializable data class PowerCheck(val check: PowerCheckKind, val value: Double = 0.0, val word: String? = null)

@Serializable
enum class PowerCheckKind {
    LIFE_BELOW,
    LIFE_ABOVE,
    LIFE_FULL,
    SHIELD_FULL,
    SHIELD_EMPTY,
    MANA_BELOW,
    MANA_ABOVE,
    FOES_AT_LEAST,
    FOES_AT_MOST,
    TARGET_RARE,
    TARGET_AILED,
    TARGET_AILMENT,
    TARGET_CURSED,
    TARGET_LIFE_BELOW,
    TARGET_LIFE_ABOVE,
    FIGHT_BEFORE,
    FIGHT_AFTER,
    SELF_AILED,
    SELF_CLEAN,
    FLASK_RUNNING,
    BARRIER_UP,
    NTH,
    SPELL,
    ATTACK,
    DAMAGE_TYPE,
    AILMENT,

    /** У героя не меньше `value` зарядов вида `word` (1.32.0). */
    CHARGES_AT_LEAST,
}

@Serializable
data class PowerEffect(
    val act: PowerAct,
    val amount: Double? = null,
    val lines: List<PowerLine> = emptyList(),
    val duration: Double? = null,
    val stacks: Int = 1,
    val of: PowerBase = PowerBase.WEAPON,
    val type: String? = null,
    val to: PowerTarget = PowerTarget.TARGET,
    val ailment: String? = null,
    val leech: Double = 0.0,
    /** [PowerAct.CHARGE] (1.32.0): вид заряда; [consume] - снять все заряды вида вместо получения `amount` (по умолчанию 1). */
    val charge: ChargeKind? = null,
    val consume: Boolean = false,
    /** Число эффекта (урон, лечение) множится на счёт [scale] в миг срабатывания, не больше [cap] (0 - без потолка) (1.32.0). */
    val scale: PowerScale? = null,
    val cap: Double = 0.0,
    /** [PowerAct.ONE_OF] (1.32.0): один из вариантов наугад, поровну; вариант - обычный эффект. */
    val options: List<PowerEffect> = emptyList(),
)

/**
 * Что делает эффект силы. С 1.31.0: [ECHO] - удар события повторяется через `duration` секунд на `amount`% своего урона
 * по той же цели, если она жива (с `type` - весь стихией `type`); [RETALIATE] - `amount`% только что принятого урона
 * бьёт всех врагов в ряду атакующего (с `type` - стихией `type`).
 */
@Serializable
enum class PowerAct {
    BUFF,
    HEX,
    HEAL,
    HURT,
    BARRIER,
    DAMAGE,
    AILMENT,
    CURSE,
    EXECUTE,
    STUN,
    DELAY,
    RUSH,
    CHARGES,
    COOLDOWNS,
    NEXT_CRIT,
    INVULNERABLE,
    CLEANSE,
    SPREAD,
    ECHO,
    RETALIATE,

    /** Заряды героя (1.32.0): получить `amount` (1) зарядов вида `charge` или, с `consume`, снять все. `CHARGES` - по-прежнему заряды фляг. */
    CHARGE,

    /** Один из `options` наугад (1.32.0). */
    ONE_OF,
}

/** Строка эффекта силы. [upto] (1.32.0) - значение тянется наугад от `value` до `upto` в миг наложения. */
@Serializable data class PowerLine(val stat: String, val op: Op = Op.ADD, val value: Double? = null, val scale: PowerScale? = null, val cap: Double = 0.0, val upto: Double? = null)

/**
 * Счёт боя, с которым растёт строка. [MOMENTUM] (1.31.0) - удары героя подряд по одной цели: смена цели сбрасывает счёт,
 * между этапами поэтапного боя он сохраняется; строке с ним обязателен потолок `cap`.
 *
 * С 1.32.0: [FRENZY_CHARGES], [POWER_CHARGES], [ENDURANCE_CHARGES] - заряды героя этого вида сейчас. [CHARGES] - по-прежнему заряды фляг.
 * Счёт надетого (пустые слоты, уникалки, осквернённые, самоцветы) - не счёт боя, а стат листа `STOCK_WORN_*` для правил листа.
 */
@Serializable
enum class PowerScale { FOES, MISSING_LIFE, SECONDS, KILLS, CURSED_FOES, AILED_FOES, SHIELD, MANA, CHARGES, MOMENTUM, FRENZY_CHARGES, POWER_CHARGES, ENDURANCE_CHARGES }

/** База числа эффекта. [PET_LIFE] (1.32.0) - максимум здоровья боевого питомца героя. */
@Serializable enum class PowerBase { WEAPON, LIFE, SHIELD, MANA, ARMOUR, EVASION, TAKEN, DEALT, TARGET_LIFE, STRENGTH, AGILITY, INTELLECT, PET_LIFE }

/** Кого достаёт эффект. [PET] (1.32.0) - боевой питомец героя: лечение на `amount`% его здоровья, благо - строки на него. */
@Serializable enum class PowerTarget { TARGET, ALL, RANDOM, OTHERS, SELF, PET }

@Serializable data class WorldGain(val gain: WorldKind, val against: List<String> = emptyList())

@Serializable enum class WorldKind { EXPERIENCE, QUANTITY, RARITY, GOLD, UNIQUE, MAP, BOOK }
