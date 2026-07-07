package features.character

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.equipment.Equipment
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
            post("/itemCreateToInventory") {
                val itemObj = call.receive<Equipment>()
                val data = repo.itemCreateToInventory(itemObj)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/itemAddToInventory") {
                val characterId = call.queryParam("characterId")
                val itemId = call.queryParam("itemId")
                val data = repo.itemAddToInventory(characterId, itemId)
                call.respond(ApiMongoResponse.ok(data))
            }
        }
    }
}