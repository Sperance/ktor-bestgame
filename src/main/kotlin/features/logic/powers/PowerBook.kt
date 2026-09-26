package features.logic.powers

import kotlinx.serialization.Serializable

/**
 * Силы уникальных и мифических предметов (0.70.0): каждая - свой стат `POWER_*`, и ни один другой
 * предмет его не несёт. Значение стата роллится строкой уникалки как обычно, а что оно делает,
 * описывает запись книги - данными, а не кодом, так что новая сила - это строка в `powers.json`.
 *
 * Сила действует одним или несколькими путями:
 * - [Power.sheet] - правила листа: конверсии, прибавки от другого стата, обнуления. Считаются одинаково
 *   сервером ([SheetPowers]) и клиентом сразу после свода модификаторов;
 * - [Power.on] - реакция в бою: событие, проверки, шанс, перезарядка и действия. Бой считает клиент;
 * - [Power.world] - вне боя: опыт, добыча, шансы уникалок, карт и книг на сервере ([WorldPowers]).
 *
 * Число, которое действие не задало само, берётся из ролла предмета: куда именно - решает [Power.roll].
 */
@Serializable
data class PowerBook(val powers: List<Power> = emptyList())

@Serializable
data class Power(
    val stat: String,
    val roll: PowerRoll = PowerRoll.AMOUNT,
    val sheet: List<SheetRule> = emptyList(),
    val on: PowerEvent? = null,
    /** Период события [PowerEvent.EVERY], в секундах. */
    val every: Double = 0.0,
    val checks: List<PowerCheck> = emptyList(),
    /** Шанс в процентах; null - всегда (или ролл, если [roll] - [PowerRoll.CHANCE]). */
    val chance: Double? = null,
    /** Перезарядка в секундах; отрицательная - раз за бой. */
    val cooldown: Double = 0.0,
    val effects: List<PowerEffect> = emptyList(),
    val world: WorldGain? = null,
)

/** Куда идёт ролл предмета: в величину действий, в шанс срабатывания или в длительность. */
@Serializable
enum class PowerRoll { AMOUNT, CHANCE, DURATION }

/**
 * Правило листа. [value] - величина, когда она не ролл: CONVERT и GAIN берут долю в процентах
 * (перенесённое умножается на [factor]), PER - прибавку за каждые [per] источника, SET ставит
 * значение, MORE умножает на `1 + value/100`.
 */
@Serializable
data class SheetRule(
    val op: SheetOp,
    val from: String? = null,
    val to: String,
    val factor: Double = 1.0,
    val per: Double = 1.0,
    val value: Double? = null,
)

@Serializable
enum class SheetOp { CONVERT, GAIN, PER, SET, MORE }

/** Событие боя, на которое отвечает сила; STANDING - не событие, а строки, действующие, пока верны проверки. */
@Serializable
enum class PowerEvent {
    STANDING, FIGHT_START, EVERY, HIT, CRIT, KILL, HIT_TAKEN, CRIT_TAKEN, BLOCK, EVADE, SKILL_USE,
    FLASK, LOW_LIFE, SHIELD_BROKEN, INFLICT, AILED, STUN, DEATH,
}

@Serializable
data class PowerCheck(val check: PowerCheckKind, val value: Double = 0.0, val word: String? = null)

/**
 * Проверка перед срабатыванием: здоровье, щит и мана героя в процентах, сколько врагов, что с целью,
 * секунды боя, каждое [NTH]-е событие, заклинание или атака, стихия удара и наложенное состояние ([word]).
 */
@Serializable
enum class PowerCheckKind {
    LIFE_BELOW, LIFE_ABOVE, LIFE_FULL, SHIELD_FULL, SHIELD_EMPTY, MANA_BELOW, MANA_ABOVE,
    FOES_AT_LEAST, FOES_AT_MOST, TARGET_RARE, TARGET_AILED, TARGET_AILMENT, TARGET_CURSED,
    TARGET_LIFE_BELOW, TARGET_LIFE_ABOVE, FIGHT_BEFORE, FIGHT_AFTER, SELF_AILED, SELF_CLEAN,
    FLASK_RUNNING, BARRIER_UP, NTH, SPELL, ATTACK, DAMAGE_TYPE, AILMENT,
}

/**
 * Действие силы. [amount] - величина (null - ролл), [of] - от чего она считается, [to] - на кого.
 * BUFF и HEX кладут [lines] на героя или на врагов на [duration] секунд, до [stacks] раз.
 */
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
    /** Доля нанесённого урона, возвращаемая здоровьем, в процентах. */
    val leech: Double = 0.0,
)

@Serializable
enum class PowerAct {
    BUFF, HEX, HEAL, HURT, BARRIER, DAMAGE, AILMENT, CURSE, EXECUTE, STUN, DELAY, RUSH, CHARGES,
    COOLDOWNS, NEXT_CRIT, INVULNERABLE, CLEANSE, SPREAD,
}

/** Строка бафа или проклятия: [value] null - ролл; [scale] умножает её на счёт боя, не больше [cap] (0 - без предела). */
@Serializable
data class PowerLine(
    val stat: String,
    val operation: String = "ADD",
    val value: Double? = null,
    val scale: PowerScale? = null,
    val cap: Double = 0.0,
)

/** Счёт боя для строки: враги, каждые 10% недостающего здоровья, секунды, убийства, проклятые и больные враги, 10% щита и маны, заряды фляг. */
@Serializable
enum class PowerScale { FOES, MISSING_LIFE, SECONDS, KILLS, CURSED_FOES, AILED_FOES, SHIELD, MANA, CHARGES }

/** От чего считается величина: оружие героя, максимумы, защиты, последний полученный или нанесённый удар, здоровье цели, атрибуты. */
@Serializable
enum class PowerBase { WEAPON, LIFE, SHIELD, MANA, ARMOUR, EVASION, TAKEN, DEALT, TARGET_LIFE, STRENGTH, AGILITY, INTELLECT }

@Serializable
enum class PowerTarget { TARGET, ALL, RANDOM, OTHERS, SELF }

/** Небоевая сила: прибавка [gain] в процентах с монстров редкостей [against] (пусто - с любых). */
@Serializable
data class WorldGain(val gain: WorldKind, val against: List<String> = emptyList())

@Serializable
enum class WorldKind { EXPERIENCE, QUANTITY, RARITY, GOLD, UNIQUE, MAP, BOOK }
