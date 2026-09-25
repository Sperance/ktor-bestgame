package config

import base.exception.model.LocaleExceptions

/**
 * Содержимое игры, вынесенное в файлы.
 *
 * Экипировка, предметы и сферы - это таблицы, а не код: у них нет ни ветвлений,
 * ни генерации, только строки с числами. Они лежат в `resources/content`
 * рядом со словарями и иконками, и правятся без пересборки.
 *
 * С 0.39.0 сюда уехали и модификаторы (`modifiers.json`, тиры - явной таблицей, как в POE), и
 * уникалки (`uniques.json`, строки уникалки сидер сам превращает в её модификаторы). Пулы
 * реестра не имеют: их состав лежит в `pools.json` (с 0.56.0), источник называет теги, см. [features.logic.pools.Pool].
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
