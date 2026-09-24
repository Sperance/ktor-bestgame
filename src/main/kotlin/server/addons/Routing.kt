package server.addons

import base.exception.ApplicationExceptions
import base.exception.BaseRepositoryExceptions
import base.exception.BaseRouteExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.EquipmentExceptions
import base.exception.model.ItemsExceptions
import base.exception.model.PropertyExceptions
import base.exception.model.UserExceptions
import base.route.ApiMongoResponse
import base.route.RouteRegistry
import config.MongoFactory
import extensions.ALL_ROUTES
import extensions.printLog
import extensions.saveChildren
import io.ktor.openapi.OpenApiInfo
import features.logic.icons.IconCache
import features.logic.icons.IconManifest
import features.logic.portraits.PortraitCache
import features.logic.portraits.PortraitManifest
import features.logic.locale.LocaleCache
import features.logic.locale.LocaleManifest
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.response.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bson.Document
import org.koin.ktor.ext.inject
import server
import SERVER_VERSION
import API_REVISION
import extensions.RouteInfo
import features.logic.world.WorldBundle
import features.logic.world.WorldManifest
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import kotlinx.serialization.Serializable
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMembers
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
fun Application.configureRouting() {
    val routeRegistry by inject<RouteRegistry>()

    routing {
        // Файлы локализации раздаются как есть: клиент читает манифест
        // locale/index.json, сверяет отпечаток и качает нужный словарь. Манифест собирает
        // сервер, как и у иконок: отпечаток считается из файла и забыть его нельзя.
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

        // Иконки устроены так же, но манифест собирает сервер: отпечаток
        // считается из самого файла, и забыть его обновить нельзя
        route("/${IconCache.FOLDER}") {
            get("/index.json") {
                call.respondText(Json.encodeToString(IconManifest.serializer(), IconCache.manifest()), ContentType.Application.Json)
            }
            get("/${IconCache.FILE}") {
                call.respondText(IconCache.document(), ContentType.Application.Json)
            }
        }

        // Портреты (с 0.29.0): манифест с отпечатком каждого файла и сами SVG по разделам.
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

        // 0.48.0: справочники мира одним файлом. Отпечаток тот же, что в static/index.json,
        // поэтому клиент с актуальной копией получает 304 и тела не качает.
        route("/${WorldBundle.FOLDER}") {
            get("/${WorldBundle.FILE}") {
                val etag = "\"${WorldBundle.hash()}\""
                call.response.header(HttpHeaders.ETag, etag)
                if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified)
                else call.respondText(WorldBundle.document(), ContentType.Application.Json)
            }
        }

        // 0.48.0: один манифест на старт - маршруты, словари, иконки, портреты и справочники.
        // Прежние манифесты остаются для старых клиентов.
        route("/static") {
            get("/index.json") {
                val manifest = StaticManifest(SERVER_VERSION, API_REVISION, ALL_ROUTES.sortedBy { it.path }, LocaleCache.manifest(),
                    IconCache.manifest(), PortraitCache.manifest(), WorldBundle.manifest())
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
            get("/exceptions") {
                call.respond(ApiMongoResponse.ok(exceptionFiles()))
            }
            // Только администратор - это проверяет доступ до маршрута. Ключ в строке запроса,
            // лежавший в исходниках, был не защитой, а паролем, известным каждому читателю.
            post("/shutdown") {
                call.respond(ApiMongoResponse.ok("system.success"))

                GlobalScope.launch {
                    delay(2.seconds)
                    server.stop()
                }
            }
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
                        call.respond(ApiMongoResponse.error(ApplicationExceptions.funExceptionDisconnected("/health")))
                    }
                } catch (e: Exception) {
                    printLog("Health check failed")
                    call.respond(ApiMongoResponse.error(ApplicationExceptions.funExceptionError("/health")))
                }
            }
            get("/version") {
                call.respond(ApiMongoResponse.ok(ServerVersion(SERVER_VERSION)))
            }
            get("/stats") {
                call.respond(ApiMongoResponse.ok(StatTables.served))
            }
            get("/routes") {
                val result = ALL_ROUTES.sortedBy { it.path }
                call.respond(ApiMongoResponse.ok(result))
            }
        }
    }.saveChildren()
}

private fun exceptionFiles(): ArrayList<String> {
    val arrayClasses = listOf(
        ApplicationExceptions::class,
        BaseRepositoryExceptions::class,
        BaseRouteExceptions::class,
        CharacterExceptions::class,
        EquipmentExceptions::class,
        ItemsExceptions::class,
        PropertyExceptions::class,
        UserExceptions::class,
    )

    val resultArray = ArrayList<String>()

    arrayClasses.forEach { cls ->
        val instance = cls.objectInstance ?: run {
            try {
                cls.constructors.firstOrNull { it.parameters.isEmpty() }?.call()
            } catch (e: Exception) {
                null
            }
        }

        if (instance == null) {
            printLog("⚠️[${cls.simpleName}] Не удалось получить экземпляр")
            return@forEach
        }

        cls.declaredMembers
            .filterIsInstance<KFunction<*>>()
            .filter { it.name.startsWith("funException") }
            .forEach { func ->
                try {
                    val args = mutableMapOf<KParameter, Any?>()

                    func.parameters.forEachIndexed { index, param ->
                        // Пропускаем receiver (индекс 0 если это метод класса)
                        if (index == 0 && param.type.classifier == cls) {
                            args[param] = instance
                            return@forEachIndexed
                        }

                        // Проверяем, есть ли значение по умолчанию для этого параметра
                        val defaultValue = "<NULL>"

                        // Используем значение по умолчанию
                        args[param] = defaultValue
                    }

                    val result = func.callBy(args)
                    resultArray.add("✅[${cls.simpleName}] ${func.name}: $result")

                } catch (e: Exception) {
                    resultArray.add("❌[${cls.simpleName}] ${func.name}: ${e.message}")
                }
            }
    }
    return resultArray
}

/** Правило [features.logic.trade.SellPrice]: доля базы за аффикс и множитель редкости. */
@Serializable
data class SellRule(val affixShare: Double, val rarity: Map<String, Double>)

/** Одна характеристика и её место в порядке подсчёта. */
@Serializable
data class StatOrder(val stat: String, val order: Int)

/**
 * Ответ `/system/stats` (с 0.41.0): клиент собирает лист героя сам по формуле сервера. [stats] -
 * порядок подсчёта характеристик (конверсия "X за каждые Y" верна, только если Y посчитан
 * раньше X), [slots] - порядок, в котором проверяются надетые вещи, [sell] - правило цены
 * торговца, по которому клиент показывает цену вещи заранее.
 */
@Serializable
data class StatTables(val stats: List<StatOrder>, val slots: List<String>, val sell: SellRule) {
    companion object {
        val served: StatTables by lazy {
            StatTables(
                (application.enums.EnumStatStock.entries + application.enums.EnumStatBool.entries +
                    application.enums.EnumStatProfession.entries + application.enums.EnumStatBattle.entries)
                    .map { StatOrder(it.name, (it as application.enums.IntEnumStat).order) }.sortedBy { it.order },
                application.enums.EnumEquipmentType.entries.map { it.name },
                SellRule(features.logic.trade.SellPrice.AFFIX_SHARE,
                    application.enums.EnumRarity.entries.associate { it.name to features.logic.trade.SellPrice.factor(it) }),
            )
        }
    }
}

/**
 * Единый манифест `static/index.json` (с 0.48.0): всё, что клиент сверяет на старте, одним
 * запросом - версия, ревизия API, маршруты (по ним клиент решает, какие экраны доступны) и
 * отпечатки словарей, иконок, портретов и справочников.
 */
@Serializable
data class StaticManifest(
    val version: String,
    val revision: Int,
    val routes: List<RouteInfo>,
    val locale: LocaleManifest,
    val icons: IconManifest,
    val portraits: PortraitManifest,
    val world: WorldManifest,
)

/** Ответ `/system/version`: клиент сверяет его с той версией, под которую собран. */
@Serializable
data class ServerVersion(val version: String)
