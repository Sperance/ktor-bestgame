package ru.descend.features.icons.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import ru.descend.domain.icons.IconCatalog
import ru.descend.domain.icons.IconCategory
import ru.descend.domain.icons.IconResolver
import ru.descend.features.icons.IconBindings
import ru.descend.shared.http.ApiMongoResponse

private val SVG = ContentType("image", "svg+xml")
private const val IMMUTABLE = "public, max-age=604800, immutable"

/**
 * Иконки отдаются без авторизации: это статичная графика набора, а не данные игрока.
 * Содержимое детерминировано, поэтому ответы кэшируются по ETag, а не пересобираются.
 */
fun Route.iconRoutes() {
    route("/api/v1/icons") {
        get {
            val category = call.request.queryParameters["category"]?.let { raw ->
                runCatching { IconCategory.valueOf(raw.uppercase()) }.getOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
            }
            val query = call.request.queryParameters["q"].orEmpty().take(100).lowercase()
            val icons = IconCatalog.icons
                .filter { category == null || it.category == category }
                .filter { art ->
                    query.isBlank() || art.id.contains(query) || art.title.lowercase().contains(query) ||
                        art.keywords.any { it.lowercase().contains(query) }
                }
            call.respond(ApiMongoResponse.ok(IconManifest(
                total = icons.size,
                categories = icons.groupingBy { it.category }.eachCount(),
                icons = icons.map(IconDescriptor::of)
            )))
        }
        get("/bindings") {
            call.respond(ApiMongoResponse.ok(IconBindingTables(
                stats = IconResolver.stats,
                tags = IconResolver.tags,
                modifierSources = IconResolver.sources.entries.associate { it.key.name to it.value },
                itemClasses = IconResolver.itemClasses,
                weapons = IconResolver.weapons.entries.associate { it.key.name to it.value },
                slots = IconResolver.slots.entries.associate { it.key.name to it.value },
                rarities = IconResolver.rarities.entries.associate { it.key.name to it.value },
                poeRarities = IconBindings.poeRarities.entries.associate { it.key.name to it.value },
                currencies = IconBindings.currencies.entries.associate { it.key.name to it.value },
                passiveKinds = IconBindings.passiveKinds.entries.associate { it.key.name to it.value },
                battleActions = IconBindings.battleActions.entries.associate { it.key.name to it.value },
                battleStatuses = IconBindings.battleStatuses.entries.associate { it.key.name to it.value },
                combatElements = IconBindings.combatElements,
                modifiers = IconBindings.bundledModifiers,
                bases = IconBindings.bundledBases
            )))
        }
        get("/sprite.svg") { call.respondSvg(IconCatalog.sprite, IconCatalog.spriteEtag) }
        get("/{icon}") {
            val name = call.parameters["icon"].orEmpty()
            if (!name.endsWith(".svg")) { call.respond(HttpStatusCode.NotFound); return@get }
            val framed = when (call.request.queryParameters["variant"]) {
                null, "framed" -> true
                "plain" -> false
                else -> { call.respond(HttpStatusCode.BadRequest); return@get }
            }
            val id = name.removeSuffix(".svg")
            val svg = IconCatalog.svg(id, framed)
            if (svg == null) { call.respond(HttpStatusCode.NotFound); return@get }
            call.respondSvg(svg, requireNotNull(IconCatalog.etag(id, framed)))
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondSvg(svg: String, etag: String) {
    response.header(HttpHeaders.ETag, etag)
    response.header(HttpHeaders.CacheControl, IMMUTABLE)
    if (request.headers[HttpHeaders.IfNoneMatch]?.split(",")?.any { it.trim() == etag } == true) {
        respond(HttpStatusCode.NotModified)
        return
    }
    respondText(svg, SVG)
}
