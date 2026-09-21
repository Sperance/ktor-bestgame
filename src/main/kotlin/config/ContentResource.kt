package config

import base.exception.model.LocaleExceptions

/**
 * Содержимое игры, вынесенное в файлы.
 *
 * Экипировка, предметы и сферы - это таблицы, а не код: у них нет ни ветвлений,
 * ни генерации, только строки с числами. Они лежат в `resources/content`
 * рядом со словарями и иконками, и правятся без пересборки.
 *
 * Модификаторы сюда не уехали намеренно: [ModifierSeeder] не таблица, а генератор -
 * он раскладывает тиры интерполяцией между лучшим и худшим, - и уникалки от него
 * неотделимы, потому что каждая порождает собственные описания модификаторов.
 *
 * Цена файла вместо кода - потеря проверки компилятором: неизвестное перечисление
 * или несуществующий код модификатора становятся ошибкой старта. Поэтому сидеры
 * проверяют прочитанное сами, а `SeedDataTest` проверяет файлы отдельно.
 */
object ContentResource {

    /**
     * Папка с содержимым внутри ресурсов.
     */
    const val FOLDER = "content"

    fun read(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
