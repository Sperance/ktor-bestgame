package server.addons

import base.exception.BaseRepositoryExceptions
import base.exception.BaseRouteExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.EquipmentExceptions
import base.exception.model.ItemsExceptions
import base.exception.model.PropertyExceptions
import base.exception.model.UserExceptions
import base.route.ApiMongoResponse
import base.route.RouteRegistry
import extensions.printLog
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
import org.koin.ktor.ext.inject
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMembers
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    val routeRegistry by inject<RouteRegistry>()

    routing {
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
        }
    }
}

private fun exceptionFiles(): ArrayList<String> {
    val arrayClasses = listOf(
        BaseRepositoryExceptions::class,
        BaseRouteExceptions::class,
        CharacterExceptions::class,
        EquipmentExceptions::class,
        ItemsExceptions::class,
        PropertyExceptions::class,
        UserExceptions::class
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