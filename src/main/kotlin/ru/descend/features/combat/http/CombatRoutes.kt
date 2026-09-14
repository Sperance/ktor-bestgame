package ru.descend.features.combat.http

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.descend.features.combat.application.CombatService
import ru.descend.infrastructure.security.actor
import ru.descend.shared.http.*

fun Route.combatRoutes() {
    val service by inject<CombatService>()
    authenticate("jwt-auth") {
        route("/api/v1/combat") {
            get("/catalog") { call.actor(); call.respond(ApiMongoResponse.ok(service.catalog())) }
            route("/characters/{id}") {
                get { call.respond(ApiMongoResponse.ok(service.current(call.parameters["id"] ?: missing(), call.actor()))) }
                post("/start") { call.respond(ApiMongoResponse.ok(service.start(call.parameters["id"] ?: missing(), call.actor(), call.receiveCommand()))) }
                post("/act") { call.respond(ApiMongoResponse.ok(service.act(call.parameters["id"] ?: missing(), call.actor(), call.receiveCommand()))) }
            }
        }
    }
}
