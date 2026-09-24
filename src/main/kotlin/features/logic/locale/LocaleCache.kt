package features.logic.locale

import base.exception.model.LocaleExceptions
import extensions.printLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Словари локализации, загруженные в память сервера.
 *
 * Файлы лежат в ресурсах (`locale/index.json`, `locale/common.json`, `locale/<код>.json`).
 * С 0.25.0 то, что не переводится, - имена экипировки, предметов и сфер, английские на любом
 * языке, - лежит один раз в [COMMON], а языковые файлы держат только переводимое. Клиенту
 * сервер отдаёт склейку общего и языкового словаря, так что для него ничего не изменилось:
 * один словарь на язык, со всеми ключами. Отпечаток считается от склейки, как это делает
 * [features.logic.icons.IconCache] для иконок, поэтому правка общего файла обновляет все языки.
 * В памяти они нужны для того, что клиенту не отдать: поиска по названию
 * на аукционе, где в документах лежат только коды.
 *
 * Читаются один раз при старте, как и остальные кэши проекта.
 */
object LocaleCache {

    /**
     * Папка со словарями внутри ресурсов.
     */
    const val FOLDER = "locale"

    /**
     * Манифест со списком языков.
     */
    const val MANIFEST = "index.json"

    /**
     * Общий словарь: строки, одинаковые на всех языках.
     */
    const val COMMON = "common.json"

    /**
     * Ключи, которые живут только в [COMMON]: английское торговое имя того, чем торгуют, одно на
     * все языки (с 0.45.0 — `.trade`; само `.name` переводится, как всё остальное).
     */
    val commonKey = Regex("""^(equipment|item)\.[^.]+\.trade$""")

    private val json = Json { ignoreUnknownKeys = true }
    private val output = Json { prettyPrint = true }
    private val strings = MapSerializer(String.serializer(), String.serializer())

    private var manifest: LocaleManifest = LocaleManifest(default = "en", languages = emptyList())
    private var bundles: Map<String, LocaleBundle> = emptyMap()
    private var documents: Map<String, String> = emptyMap()

    fun manifest(): LocaleManifest = manifest

    fun languages(): List<String> = manifest.languages.map { it.code }

    fun isEmpty(): Boolean = bundles.isEmpty()

    /**
     * Словарь языка. Незнакомый язык - ошибка запроса, а не тихий откат:
     * иначе игрок увидел бы чужой язык и не понял, почему.
     *
     * @throws LocaleExceptions.LocaleException если языка нет в манифесте
     */
    fun bundle(language: String): LocaleBundle =
        bundles[language] ?: throw LocaleExceptions.funExceptionUnknownLanguage("bundle", language)

    /**
     * Язык по умолчанию из манифеста.
     */
    fun defaultLanguage(): String = manifest.default

    /**
     * Читает манифест, общий словарь и все объявленные языки и склеивает их.
     *
     * Ключ, который есть и в общем, и в языковом файле, - ошибка старта: какая из двух строк
     * верная, сервер решать не берётся, а тихий выбор одной спрятал бы вторую навсегда.
     */
    fun initializeCache() {
        val declared = json.decodeFromString(LocaleManifest.serializer(), resource(MANIFEST))
        val common = json.decodeFromString(strings, resource(COMMON))

        bundles = declared.languages.associate { language ->
            val own = json.decodeFromString(strings, resource("${language.code}.json"))
            val clash = own.keys intersect common.keys
            if (clash.isNotEmpty())
                throw LocaleExceptions.funException("initializeCache", "${language.code}.json repeats $COMMON: ${clash.take(5)}")
            language.code to LocaleBundle(language.code, (common + own).toSortedMap())
        }
        documents = bundles.mapValues { (_, bundle) -> output.encodeToString(strings, bundle.strings) }

        // Отпечаток считается из того, что отдаётся, а не берётся из манифеста: отпечаток,
        // который правят руками, перестаёт работать ровно тогда, когда он нужен -
        // строку поправили, забыли обновить число, и клиенты об этом не узнают никогда.
        manifest = declared.copy(languages = declared.languages.map {
            it.copy(hash = sha256(documents.getValue(it.code)))
        })

        printLog("[LocaleCache] initialized: ${bundles.entries.joinToString { "${it.key}=${it.value.size}" }}, common=${common.size}")
    }

    /**
     * Тело словаря языка: общий словарь и языковой, склеенные в один.
     */
    fun document(language: String): String =
        documents[language] ?: throw LocaleExceptions.funExceptionUnknownLanguage("document", language)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private fun resource(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
