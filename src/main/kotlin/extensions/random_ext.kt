package extensions

import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom
import kotlin.random.nextInt
import kotlin.random.nextLong

/**
 * Общий генератор сервера (с 0.49.0 - потоковый): один `Random` на все запросы
 * не потокобезопасен и при гонке выдавал повторы.
 */
object RandomExt {

    val random: Random get() = ThreadLocalRandom.current().asKotlinRandom()

    /* INT */

    fun randomInt(min: Int, max: Int) = random.nextInt(min, max)
    fun randomInt(range: IntRange) = random.nextInt(range)

    /* DOUBLE */

    fun randomDouble(min: Double, max: Double) = random.nextDouble(min, max)

    /**
     * Качество ролла в диапазоне [0.0, 1.0): 0 - минимум диапазона, ближе к 1 - максимум.
     */
    fun randomProgress() = random.nextDouble()

    /* LONG */

    fun randomLong(min: Long, max: Long) = random.nextLong(min, max)
    fun randomLong(range: LongRange) = random.nextLong(range)
}

fun IntRange.randomExt(): Int {
    return RandomExt.randomInt(this)
}

fun LongRange.randomExt(): Long {
    return RandomExt.randomLong(this)
}

fun <T> Collection<T>.randomExt(): T {
    if (isEmpty()) throw NoSuchElementException("Collection is empty.")
    return elementAt(RandomExt.randomInt(indices))
}

/**
 * Взвешенный случайный выбор элемента коллекции.
 *
 * Элементы с неположительным весом игнорируются. Вес каждого элемента
 * спрашивается один раз. Возвращает null, если подходящих элементов нет.
 */
fun <T> Collection<T>.weightedRandomExt(weight: (T) -> Int): T? {
    if (isEmpty()) return null
    val weights = IntArray(size)
    var total = 0L
    forEachIndexed { index, item -> weights[index] = weight(item).coerceAtLeast(0); total += weights[index] }
    if (total == 0L) return null
    var point = RandomExt.randomLong(0, total)
    forEachIndexed { index, item ->
        point -= weights[index]
        if (point < 0) return item
    }
    return null
}
