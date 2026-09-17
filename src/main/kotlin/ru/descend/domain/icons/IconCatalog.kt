package ru.descend.domain.icons

import java.security.MessageDigest

/**
 * Набор иконок сервера: поиск по идентификатору, готовый SVG и версия набора.
 *
 * Картинки детерминированы: из одного набора всегда получается один и тот же байт-в-байт SVG,
 * поэтому ETag и версия считаются один раз при старте и клиент может кэшировать их вечно.
 */
object IconCatalog {
    /** Иконка, которой подписывается всё, для чего нет отдельного рисунка. */
    const val FALLBACK = "ui-unknown"

    val icons: List<IconArt> = IconLibrary.all.sortedBy { it.id }
    val byId: Map<String, IconArt> = icons.associateBy { it.id }

    init {
        require(icons.size == byId.size) { "Duplicate icon id in the library" }
        require(FALLBACK in byId)
    }

    private val framed: Map<String, String> = icons.associate { it.id to IconRenderer.svg(it, framed = true) }
    private val plain: Map<String, String> = icons.associate { it.id to IconRenderer.svg(it, framed = false) }

    val sprite: String by lazy { IconRenderer.sprite(icons) }
    val spriteEtag: String by lazy { etagOf(sprite) }

    /** Версия набора. Меняется при любой правке рисунка, цвета или состава. */
    val version: String by lazy {
        digest(icons.joinToString("|") { "${it.id}:${it.category}:${it.tint}:${it.deep}:${it.body}" })
    }

    fun art(id: String): IconArt? = byId[id]
    fun exists(id: String) = id in byId
    fun categories(): Map<IconCategory, List<String>> = icons.groupBy({ it.category }, { it.id })

    fun svg(id: String, framed: Boolean = true): String? = if (framed) this.framed[id] else plain[id]
    fun etag(id: String, framed: Boolean = true): String? = svg(id, framed)?.let { etagOf(it) }

    /** Путь, по которому клиент забирает картинку. Относительный: хост задаёт само приложение. */
    fun url(id: String) = "/api/v1/icons/$id.svg"

    private fun etagOf(value: String) = "\"${digest(value)}\""
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }
}
