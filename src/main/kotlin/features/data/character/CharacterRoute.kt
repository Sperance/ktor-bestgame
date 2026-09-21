package features.data.character

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.data.character.character_data.CharacterItems
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
        // Персонажи одного игрока: экран выбора при входе читает только их.
        get("/byUser") {
            val userId = call.queryParam("userId")
            val data = repo.findByUser(userId)
            call.respond(ApiMongoResponse.ok(data))
        }

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
                val equipmentId = call.queryParam("equipmentId")
                val data = repo.itemToInventory(characterId, equipmentId)
                call.respond(ApiMongoResponse.ok(data))
            }
            get("/stats") {
                val characterId = call.queryParam("characterId")
                val data = repo.calculateStats(characterId)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/experience") {
                val characterId = call.queryParam("characterId")
                val amount = call.queryParam("amount", 0.0)
                val data = repo.addExperience(characterId, amount)
                call.respond(ApiMongoResponse.ok(data))
            }
            get("/items") {
                val characterId = call.queryParam("characterId")
                val character = repo.findById(characterId)
                call.respond(ApiMongoResponse.ok(character?.parseItems()))
            }
            post("/addItem") {
                val characterId = call.queryParam("characterId")
                val itemObj = call.receive<List<CharacterItems>>()
                val data = repo.addItem(characterId, itemObj)
                call.respond(ApiMongoResponse.ok(data))
            }
        }

        route("/skilltree") {
            get("/state") {
                val characterId = call.queryParam("characterId")
                val data = repo.skillTreeState(characterId)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/allocate") {
                val characterId = call.queryParam("characterId")
                val nodeCode = call.queryParam("nodeCode")
                val data = repo.allocateSkillNode(characterId, nodeCode)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/refund") {
                val characterId = call.queryParam("characterId")
                val nodeCode = call.queryParam("nodeCode")
                val data = repo.refundSkillNode(characterId, nodeCode)
                call.respond(ApiMongoResponse.ok(data))
            }
            post("/reset") {
                val characterId = call.queryParam("characterId")
                val data = repo.resetSkillTree(characterId)
                call.respond(ApiMongoResponse.ok(data))
            }
        }
    }
}