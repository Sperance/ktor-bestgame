package features.data.inventory

import base.route.ApiMongoResponse
import base.route.BaseRoute
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

class CharacterEquipmentRoute(
    val repo: CharacterEquipmentRepository
) : BaseRoute<CharacterEquipment, CharacterEquipment>(
    repository = repo,
    entitySerializer = CharacterEquipment.serializer(),
    responseSerializer = CharacterEquipment.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/byCharacter") {
            val characterId = call.queryParam("characterId")
            val data = repo.findByCharacter(characterId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/equip") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val data = repo.equip(characterId, inventoryId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/unequip") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val data = repo.unequip(characterId, inventoryId)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
