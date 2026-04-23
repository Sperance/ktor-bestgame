package extensions

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

private var randomSeed = 1L

object RandomExt {

    val random = Random(System.currentTimeMillis() + randomSeed)
        get() {
            randomSeed++
            if (randomSeed == Long.MAX_VALUE - 1) randomSeed = 1
            return field
        }

    /* INT */

    fun randomInt(min: Int, max: Int) = random.nextInt(min, max)
    fun randomInt(range: IntRange) = random.nextInt(range)

    /* DOUBLE */

    fun randomDouble(min: Double, max: Double) = random.nextDouble(min, max)

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
    if (isEmpty())
        throw NoSuchElementException("Collection is empty.")
    return elementAt(RandomExt.randomInt(0..<size))
}