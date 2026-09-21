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
 * Файлы лежат в ресурсах (`locale/index.json`, `locale/<код>.json`). Тела словарей
 * отдаются как есть, а манифест сервер пересобирает: отпечаток каждого языка он
 * считает из самого файла, как это делает [features.logic.icons.IconCache].
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
        val declared = json.decodeFromString(LocaleManifest.serializer(), resource(MANIFEST))
        val documents = declared.languages.associate { it.code to resource("${it.code}.json") }

        // Отпечаток считается из самого файла, а не берётся из манифеста: отпечаток,
        // который правят руками, перестаёт работать ровно тогда, когда он нужен -
        // строку поправили, забыли обновить число, и клиенты об этом не узнают никогда.
        manifest = declared.copy(languages = declared.languages.map {
            it.copy(hash = sha256(documents.getValue(it.code)))
        })

        bundles = declared.languages.associate { language ->
            val strings = json.decodeFromString(
                MapSerializer(String.serializer(), String.serializer()),
                documents.getValue(language.code)
            )
            language.code to LocaleBundle(language.code, strings)
        }

        printLog("[LocaleCache] initialized: ${bundles.entries.joinToString { "${it.key}=${it.value.size}" }}")
    }

    /**
     * Тело словаря - тот же текст, что лежит в ресурсах.
     */
    fun document(language: String): String = resource("$language.json")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private fun resource(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
