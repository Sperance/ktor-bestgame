package features.logic.locale

import kotlinx.serialization.Serializable

/**
 * Описание одного языка в манифесте.
 *
 * @property code код языка, он же имя файла без расширения
 * @property label подпись языка для меню выбора, всегда на самом этом языке
 * @property hash отпечаток файла: клиент качает словарь заново,
 * только если отпечаток разошёлся с сохранённым
 */
@Serializable
data class LocaleLanguage(
    val code: String,
    val label: String,
    val hash: String,
)

/**
 * Манифест `locale/index.json`: что за языки есть и какой из них
 * предлагать, пока игрок не выбрал свой.
 */
@Serializable
data class LocaleManifest(
    val default: String,
    val languages: List<LocaleLanguage>,
)

/**
 * Словарь одного языка - плоская таблица "ключ - строка".
 *
 * Отсутствующий ключ возвращается сам собой: дыру видно сразу,
 * и её не спишут на молчаливый откат к другому языку.
 */
class LocaleBundle(
    val language: String,
    private val strings: Map<String, String>,
) {

    val size: Int get() = strings.size

    val keys: Set<String> get() = strings.keys

    fun contains(key: String): Boolean = key in strings

    /**
     * Строка по ключу. Ключа нет - вернётся сам ключ.
     */
    operator fun get(key: String): String = strings[key] ?: key

    /**
     * Коды раздела, чьё название содержит [text].
     *
     * Так поиск по тексту превращается в поиск по кодам: названий в
     * документах нет, а Mongo о словаре ничего не знает.
     *
     * @param section раздел ключа, см. [LocaleKey]
     */
    fun codesMatching(section: String, text: String): List<String> {
        val needle = text.trim()
        if (needle.isEmpty()) return emptyList()

        val prefix = "$section."
        val suffix = ".${LocaleKey.NAME}"

        return strings.asSequence()
            .filter { it.key.startsWith(prefix) && it.key.endsWith(suffix) }
            .filter { it.value.contains(needle, ignoreCase = true) }
            .map { it.key.removePrefix(prefix).removeSuffix(suffix) }
            .toList()
    }
}
