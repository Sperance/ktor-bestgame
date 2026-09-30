package server.addons

import API_REVISION
import SERVER_VERSION
import base.exception.ApplicationExceptions
import base.route.ApiMongoResponse
import base.route.RouteRegistry
import config.ContentManifest
import config.ContentStore
import config.MongoFactory
import extensions.ALL_ROUTES
import extensions.RouteInfo
import extensions.printLog
import extensions.saveChildren
import features.logic.hero.installHeroLocks
import features.logic.icons.IconCache
import features.logic.icons.IconManifest
import features.logic.locale.LocaleCache
import features.logic.locale.LocaleManifest
import features.logic.portraits.PortraitCache
import features.logic.portraits.PortraitManifest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bson.Document
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val routeRegistry by inject<RouteRegistry>()
    val content by inject<ContentStore>()
    installHeroLocks()
    installIdempotency()
    routing {
        // Словари, иконки и портреты - статичные файлы с манифестами; отпечатки считает сервер,
        // и те же манифесты собраны в static/index.json.
        route("/${LocaleCache.FOLDER}") {
            get("/${LocaleCache.MANIFEST}") {
                call.respondText(Json.encodeToString(LocaleManifest.serializer(), LocaleCache.manifest()), ContentType.Application.Json)
            }
            get("/{language}.json") {
                val language = call.parameters["language"].orEmpty()
                LocaleCache.bundle(language)
                call.respondText(LocaleCache.document(language), ContentType.Application.Json)
            }
        }
        route("/${IconCache.FOLDER}") {
            get("/index.json") {
                call.respondText(Json.encodeToString(IconManifest.serializer(), IconCache.manifest()), ContentType.Application.Json)
            }
            get("/${IconCache.FILE}") {
                call.respondText(IconCache.document(), ContentType.Application.Json)
            }
        }
        route("/${PortraitCache.FOLDER}") {
            get("/${PortraitCache.MANIFEST}") {
                call.respondText(Json.encodeToString(PortraitManifest.serializer(), PortraitCache.manifest()), ContentType.Application.Json)
            }
            get("/{section}/{file}") {
                val section = call.parameters["section"].orEmpty()
                val code = call.parameters["file"].orEmpty().removeSuffix(".svg")
                val body = PortraitCache.document(section, code)
                if (body == null) call.respond(HttpStatusCode.NotFound)
                else call.respondText(body, ContentType.Image.SVG)
            }
        }

        // 1.0.0: контент чанками - файл за файлом, каждый со своим отпечатком. Клиент с актуальной
        // копией получает 304 и тела не качает; тело уходит сжатым, если клиент его принимает.
        route("/content") {
            get("/{file}") {
                val chunk = content.chunk(call.parameters["file"].orEmpty()) ?: return@get call.respond(HttpStatusCode.NotFound)
                val etag = "\"${chunk.hash}\""
                call.response.header(HttpHeaders.ETag, etag)
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) return@get call.respond(HttpStatusCode.NotModified)
                if (call.request.headers[HttpHeaders.AcceptEncoding]?.contains("gzip") == true) {
                    call.response.header(HttpHeaders.ContentEncoding, "gzip")
                    call.respondBytes(chunk.gzip, ContentType.Application.Json)
                } else call.respondText(chunk.text, ContentType.Application.Json)
            }
        }

        // 0.48.0: один манифест на старт - маршруты, словари, иконки, портреты и контент.
        route("/static") {
            get("/index.json") {
                val manifest = StaticManifest(SERVER_VERSION, API_REVISION, ALL_ROUTES.sortedBy { it.path }, LocaleCache.manifest(),
                    IconCache.manifest(), PortraitCache.manifest(), content.manifest)
                call.respondText(Json.encodeToString(StaticManifest.serializer(), manifest), ContentType.Application.Json)
            }
        }

        routeRegistry.registerAll(this)

        openAPI(path = "swagger") {
            info = OpenApiInfo("My API", "1.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }

        route("/system") {
            get("/health") {
                try {
                    val ping = MongoFactory.getDatabase().runCommand(Document("ping", 1))
                    if (ping.getDouble("ok") == 1.0) {
                        call.respond(ApiMongoResponse.ok(mapOf(
                            "status" to "ok",
                            "database" to "connected",
                            "timestamp" to System.currentTimeMillis()
                        ).toString()))
                    } else {
                        call.respond(HttpStatusCode.ServiceUnavailable, ApiMongoResponse.error(ApplicationExceptions.funExceptionDisconnected("/health")))
                    }
                } catch (e: Exception) {
                    // База недоступна - 503 (1.53.0): балансировщик и клиент видят это по статусу, а не по телу
                    printLog("Health check failed: ${e.message}")
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiMongoResponse.error(ApplicationExceptions.funExceptionError("/health")))
                }
            }
            // Готовность сервера и список маршрутов для проверки клиента (скрипт client-server в CI).
            get("/routes") {
                call.respond(ApiMongoResponse.ok(ALL_ROUTES.sortedBy { it.path }))
            }
        }
    }.saveChildren()
}

/**
 * Единый манифест `static/index.json`: всё, что клиент сверяет на старте, одним запросом - версия,
 * ревизия API, маршруты (по ним клиент решает, какие экраны доступны) и отпечатки словарей, иконок,
 * портретов и чанков контента.
 */
@Serializable
data class StaticManifest(
    val version: String,
    val revision: Int,
    val routes: List<RouteInfo>,
    val locale: LocaleManifest,
    val icons: IconManifest,
    val portraits: PortraitManifest,
    val content: ContentManifest,
)
