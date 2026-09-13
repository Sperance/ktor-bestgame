package ru.descend.features.character.http

import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.CharacterItems

import ru.descend.features.character.persistence.CharacterRepository

import ru.descend.shared.http.ApiMongoResponse
import ru.descend.shared.http.BaseRoute
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class CharacterRoute(
    val repo: CharacterRepository
) : BaseRoute<Character, Character>(
    repository = repo,
    entitySerializer = Character.serializer(),
    responseSerializer = Character.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        route("/inventory") {
            get("/equipments") {
                val characterId = call.queryParam("characterId")
                val data = repo.getEquipmentsData(characterId)
                call.respond(ApiMongoResponse.ok(data))
            }
            get("/equipped") {
                val characterId = call.queryParam("characterId")
                val data = repo.getEquippedData(characterId)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/itemToInventory") {
                val characterId = call.queryParam("characterId")
                val itemObj = call.receive<CharacterEquipments>()
                val data = repo.itemToInventory(characterId, itemObj)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/addItem") {
                val characterId = call.queryParam("characterId")
                val itemObj = call.receive<List<CharacterItems>>()
                val data = repo.addItem(characterId, itemObj)
                call.respond(ApiMongoResponse.ok(data))
            }
        }
    }
}
