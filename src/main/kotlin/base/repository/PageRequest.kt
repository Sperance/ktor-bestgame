package base.repository

import CONST_PAGE_SIZE_MAX

/**
 * Запрошенная страница, приведённая к безопасным значениям.
 *
 * Отдельный тип, а не пара чисел: именно здесь живёт вся арифметика
 * постраничного вывода, и проверяется она без Mongo.
 *
 * Номер страницы считается с нуля. Применённые значения возвращаются
 * клиенту в `PagedMongoResponse`, поэтому видно, какая страница и какой
 * размер сработали на самом деле.
 */
data class PageRequest(
    val page: Int,
    val size: Int,
) {

    /**
     * Сколько документов пропустить до начала страницы.
     *
     * Считается в Long и обрезается по Int: номер страницы приходит из
     * запроса, и на больших числах `page * size` переполнился бы, а
     * отрицательный skip драйвер не принимает.
     */
    val skip: Int = (page.toLong() * size).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Сколько всего страниц при таком размере.
     */
    fun totalPages(totalItems: Long): Int =
        if (totalItems <= 0) 0 else ((totalItems + size - 1) / size).toInt()

    companion object {
        /**
         * Приводит запрошенные значения к допустимым: страница не бывает
         * отрицательной, размер - меньше одного и больше [CONST_PAGE_SIZE_MAX].
         */
        fun of(page: Int, size: Int): PageRequest = PageRequest(
            page = page.coerceAtLeast(0),
            size = size.coerceIn(1, CONST_PAGE_SIZE_MAX)
        )
    }
}
