package ru.descend.exileforge.base.route
import io.ktor.server.routing.Routing

class RouteRegistry(
    private val routes: List<RouteRegistrar>,
) {
    fun registerAll(routing: Routing) {
        routes.forEach { it.register(routing) }
    }
}
