package features.logic.icons

import base.exception.model.LocaleExceptions
import extensions.printLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/**
 * Набор иконок, загруженный в память сервера.
 *
 * Файл лежит в ресурсах (`icons/icons.json`) и отдаётся клиенту как есть -
 * сервер его не собирает. В памяти он нужен ради отпечатка: клиент качает
 * тело, только если отпечаток разошёлся с сохранённым.
 *
 * Отпечаток считается из самого файла при старте, а не хранится рядом с ним
 * вручную. Отпечаток, который можно забыть обновить, ровно в тот момент и
 * перестаёт работать, когда он нужен: иконку поправили, а клиенты об этом
 * никогда не узнают.
 *
 * Читается один раз при старте, как и остальные кэши проекта.
 */
object IconCache {

    /**
     * Папка с иконками внутри ресурсов.
     */
    const val FOLDER = "icons"

    /**
     * Файл с рисунками и таблицей "код - рисунок".
     */
    const val FILE = "icons.json"

    private val json = Json { ignoreUnknownKeys = true }

    private var document: String = "{}"
    private var fingerprint: String = ""
    private var sprites: Int = 0
    private var keys: Int = 0

    /**
     * Тело набора - тот же текст, что отдаётся клиенту.
     */
    fun document(): String = document

    /**
     * Отпечаток тела: по нему клиент решает, качать заново или нет.
     */
    fun hash(): String = fingerprint

    fun isEmpty(): Boolean = keys == 0

    /**
     * Манифест `icons/index.json`, собранный вокруг отпечатка.
     */
    fun manifest(): IconManifest = IconManifest(hash = fingerprint, file = FILE, sprites = sprites, icons = keys)

    fun initializeCache() {
        document = resource(FILE)
        fingerprint = sha256(document)

        val parsed = json.parseToJsonElement(document).jsonObject
        sprites = parsed["sprites"]?.jsonObject?.size ?: 0
        keys = parsed["icons"]?.jsonObject?.size ?: 0

        printLog("[IconCache] initialized: sprites=$sprites icons=$keys hash=$fingerprint")
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private fun resource(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
