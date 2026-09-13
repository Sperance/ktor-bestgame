package ru.descend.infrastructure.http

import ru.descend.features.poe.http.poeRoutes
import ru.descend.shared.error.ApplicationExceptions
import ru.descend.shared.error.BaseRepositoryExceptions
import ru.descend.shared.error.BaseRouteExceptions
import ru.descend.shared.error.model.CharacterExceptions
import ru.descend.shared.error.model.EquipmentExceptions
import ru.descend.shared.error.model.ItemsExceptions
import ru.descend.shared.error.model.PropertyExceptions
import ru.descend.shared.error.model.UserExceptions
import ru.descend.shared.http.ApiMongoResponse
import ru.descend.shared.http.RouteRegistry
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.shared.extensions.ALL_ROUTES
import ru.descend.shared.extensions.printLog
import ru.descend.shared.extensions.saveChildren
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bson.Document
import org.koin.ktor.ext.inject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMembers
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
fun Application.configureRouting() {
    val routeRegistry by inject<RouteRegistry>()

    routing {
        routeRegistry.registerAll(this)
        poeRoutes()

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
            get("/shutdown") {

                val key = call.queryParameters["key"]
                if (key == null || key != "32543254") {
                    call.respond(ApiMongoResponse.ok("Access denied"))
                    return@get
                }

                call.respond(ApiMongoResponse.ok("Success"))

                GlobalScope.launch {
                    delay(2.seconds)
                    call.application.engine.stop()
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
