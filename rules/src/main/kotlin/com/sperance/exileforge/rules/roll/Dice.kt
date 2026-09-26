package com.sperance.exileforge.rules.roll

import kotlin.math.floor
import kotlin.random.Random

/**
 * Кости правил: обёртка над [Random] Kotlin, чей посев одинаков на JVM и Android, - так сервер и
 * клиент, бросив одно семя, получают одни числа. Все правила бросают только через неё.
 */
class Dice(private val random: Random) {
    constructor(seed: Long) : this(Random(seed))

    fun nextDouble(): Double = random.nextDouble()
    fun nextInt(bound: Int): Int = random.nextInt(bound)
    fun nextLong(bound: Long): Long = random.nextLong(bound)
    /** Целое от [from] до [until] включительно; вырожденный диапазон - его начало. */
    fun between(from: Int, until: Int): Int = if (until > from) random.nextInt(from, until + 1) else from
    fun between(from: Long, until: Long): Long = if (until > from) random.nextLong(from, until + 1) else from
    fun between(range: List<Int>): Int = between(range[0], range.getOrElse(1) { range[0] })
    fun betweenLong(range: List<Long>): Long = between(range[0], range.getOrElse(1) { range[0] })
    /** Доля ролла `[0, 1)`, округлённая до тысячных: столько и хранится на копии. */
    fun share(): Double = floor(random.nextDouble() * 1000) / 1000
    /** Событие с шансом [percent] процентов. */
    fun percent(percent: Double): Boolean = random.nextDouble() * 100 < percent
    /** Событие с шансом [chance] от 0 до 1. */
    fun chance(chance: Double): Boolean = random.nextDouble() < chance
    /** Дробное ожидание целиком: целая часть и остаток шансом. */
    fun times(expected: Double): Int {
        val whole = floor(expected).toInt()
        return whole + if (random.nextDouble() < expected - whole) 1 else 0
    }
    fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]
    fun <T> pickOrNull(list: List<T>): T? = if (list.isEmpty()) null else pick(list)
    fun <T> shuffled(list: List<T>): List<T> = list.shuffled(random)

    companion object {
        /** Кости на системном генераторе - где детерминизм не нужен. */
        fun system(): Dice = Dice(Random.Default)
    }
}

/**
 * Потоки одного семени: каждому событию захода - свои кости, выведенные из семени и ключа события.
 * Так порядок событий на клиенте ничего не меняет, а сервер проигрывает каждое независимо.
 */
class Streams(val seed: Long) {
    fun of(kind: String, index: Int = 0): Dice = Dice(mix(seed, kind.hashCode().toLong(), index.toLong()))

    companion object {
        /** Смесь семени и ключей - вариант SplitMix64: соседние ключи дают далёкие семена. */
        fun mix(seed: Long, a: Long, b: Long): Long {
            var z = seed xor (a * -7046029254386353131L) xor (b * 0x9E3779B97F4A7C15uL.toLong())
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }
    }
}
