package features.logic.locale

import base.exception.model.LocaleExceptions
import extensions.printLog
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Словари локализации, загруженные в память сервера.
 *
 * Файлы лежат в ресурсах (`locale/index.json`, `locale/<код>.json`) и
 * раздаются клиенту статикой как есть - сервер их не собирает и не меняет.
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

    private val json = Json { ignoreUnknownKeys = true }

    private var manifest: LocaleManifest = LocaleManifest(default = "en", languages = emptyList())
    private var bundles: Map<String, LocaleBundle> = emptyMap()

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
     * Читает манифест и все объявленные в нём словари из ресурсов.
     */
    fun initializeCache() {
        manifest = json.decodeFromString(LocaleManifest.serializer(), resource(MANIFEST))

        bundles = manifest.languages.associate { language ->
            val strings = json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                resource("${language.code}.json")
            )
            language.code to LocaleBundle(language.code, strings)
        }

        printLog("[LocaleCache] initialized: ${bundles.entries.joinToString { "${it.key}=${it.value.size}" }}")
    }

    private fun resource(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
