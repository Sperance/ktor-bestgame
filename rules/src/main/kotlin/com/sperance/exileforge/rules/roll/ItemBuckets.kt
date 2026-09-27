package com.sperance.exileforge.rules.roll

/**
 * Вещи героя в снимке (1.1.0) лежат не одной частью, а корзинами по id копии: смена одной вещи
 * пересылает её корзину - около шестнадцатой доли тайника, - а не весь тайник. Порядок вещей идёт
 * своей частью [ORDER]: список id, по которому клиент собирает корзины обратно.
 */
object ItemBuckets {
    const val COUNT = 16
    const val PREFIX = "items."
    const val ORDER = "items.order"

    /** Имена частей-корзин по порядку. */
    val names: List<String> = List(COUNT) { "$PREFIX$it" }

    /** Корзина копии: хеш строки стабилен на любой JVM, так что сервер и клиент считают её одинаково. */
    fun of(id: String): Int = Math.floorMod(id.hashCode(), COUNT)
}
