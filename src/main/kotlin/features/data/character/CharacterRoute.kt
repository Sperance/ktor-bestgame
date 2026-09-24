package features.data.character

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.logic.campaign.CampaignService
import features.data.character.character_data.CharacterItems
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class CharacterRoute(
    val repo: CharacterRepository,
    val campaign: CampaignService,
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

        // Кампания (0.26.0): бой считает клиент по правилам сервера (0.28.0), добычу, опыт и цену смерти - сервер.
        route("/campaign") {
            get("/chapters") {
                call.respond(ApiMongoResponse.ok(campaign.view()))
            }
            get("/progress") {
                val characterId = call.queryParam("characterId")
                call.respond(ApiMongoResponse.ok(campaign.progress(characterId)))
            }
            post("/kill") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                val monsterCode = call.queryParam("monsterCode")
                val rarity = call.queryParam("rarity")
                call.respond(ApiMongoResponse.ok(campaign.kill(characterId, mapCode, monsterCode, rarity)))
            }
            post("/complete") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.complete(characterId, mapCode)))
            }
            // 0.28.0: смерть героя стоит опыта по правилу сервера; уровень не падает.
            post("/fall") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.fall(characterId, mapCode)))
            }
            // 0.31.0: сундуки - окно в шесть часов на карту у каждого героя, добыча - сервера.
            get("/chests") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.chests(characterId, mapCode)))
            }
            post("/chest") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.openChest(characterId, mapCode)))
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