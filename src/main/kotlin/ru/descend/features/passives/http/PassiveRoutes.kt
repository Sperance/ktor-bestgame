package ru.descend.features.passives.http

import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.descend.features.passives.application.PassiveService
import ru.descend.infrastructure.security.actor
import ru.descend.shared.http.*

fun Route.passiveRoutes() {
    val service by inject<PassiveService>()
    authenticate("jwt-auth") {
        route("/api/v1/passives") {
            get("/tree") {
                call.actor()
                val raw = call.request.queryParameters["revision"]
                val revision = if (raw == null) 1 else raw.toIntOrNull()?.takeIf { it > 0 } ?: invalid("Invalid tree revision")
                call.respond(ApiMongoResponse.ok(service.tree(revision)))
            }
            get("/characters/{id}") { call.respond(ApiMongoResponse.ok(service.state(checkedId(call.parameters["id"]), call.actor()))) }
            post("/characters/{id}") { call.respond(ApiMongoResponse.ok(service.change(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        }
    }
}
