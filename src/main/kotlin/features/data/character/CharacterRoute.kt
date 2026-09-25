package features.data.character

import base.exception.model.SkillTreeExceptions
import features.logic.hero.HeroSnapshots
import features.logic.hero.respondWithHero
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
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
    val merchant: features.logic.trade.MerchantService,
    val crafts: features.logic.crafts.CraftsService,
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

        // 0.48.0: герой одним запросом - тот же снимок, что приходит в ответ команды, с ETag.
        get("/view") {
            val characterId = call.queryParam("characterId")
            val snapshot = HeroSnapshots.of(characterId, HeroSnapshots.known(call.request.headers[HeroSnapshots.HEADER]))
            val etag = "\"${snapshot.version}\""
            call.response.header(HttpHeaders.ETag, etag)
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified)
            else call.respond(ApiMongoResponse.ok(snapshot))
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
                call.respondWithHero(data)
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
                call.respondWithHero(data)
            }
            get("/items") {
                val characterId = call.queryParam("characterId")
                // 0.37.0: добытое работой ложится в сумку до того, как её прочтут.
                crafts.settle(characterId)
                val character = repo.findById(characterId)
                call.respond(ApiMongoResponse.ok(character?.parseItems()))
            }
            post("/addItem") {
                val characterId = call.queryParam("characterId")
                val itemObj = call.receive<List<CharacterItems>>()
                val data = repo.addItem(characterId, itemObj)
                call.respondWithHero(data)
            }
        }

        // Ремёсла (0.37.0): работа идёт на сервере по времени и досчитывается при каждом обращении.
        route("/crafts") {
            get {
                val characterId = call.queryParam("characterId")
                call.respond(ApiMongoResponse.ok(crafts.state(characterId)))
            }
            post("/start") {
                val characterId = call.queryParam("characterId")
                val job = call.queryParam("job")
                // 0.38.0: примеси кузнеца - коды через запятую.
                val additives = call.request.queryParameters["additives"]?.split(',').orEmpty()
                call.respondWithHero(crafts.start(characterId, job, additives))
            }
            post("/stop") {
                val characterId = call.queryParam("characterId")
                call.respondWithHero(crafts.stop(characterId))
            }
        }

        // Торговец (0.34.0): витрина героя раз в четыре часа и покупка с неё за золото.
        route("/merchant") {
            get {
                val characterId = call.queryParam("characterId")
                call.respond(ApiMongoResponse.ok(merchant.stock(characterId)))
            }
            post("/buy") {
                val characterId = call.queryParam("characterId")
                val offerId = call.queryParam("offerId")
                call.respond(ApiMongoResponse.ok(merchant.buy(characterId, offerId)))
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
                call.respondWithHero(campaign.kill(characterId, mapCode, monsterCode, rarity))
            }
            post("/complete") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.complete(characterId, mapCode))
            }
            // 0.28.0: смерть героя стоит опыта по правилу сервера; уровень не падает.
            post("/fall") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.fall(characterId, mapCode))
            }
            // 0.31.0: сундуки - окно в шесть часов на карту у каждого героя, добыча - сервера.
            get("/chests") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.chests(characterId, mapCode)))
            }
            // 0.32.0: босс карты - страж выхода, возвращается через час после смерти.
            get("/boss") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respond(ApiMongoResponse.ok(campaign.boss(characterId, mapCode)))
            }
            post("/boss") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.slayBoss(characterId, mapCode))
            }
            // 0.34.0: услуги карты за золото - ещё один сундук и вызов убитого стража.
            post("/treasure") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.treasure(characterId, mapCode))
            }
            post("/summon") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.summon(characterId, mapCode))
            }
            // 0.35.0: вход в локацию - с картой нужного уровня или без неё.
            post("/start") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                val itemId = call.request.queryParameters["itemId"]
                call.respondWithHero(campaign.start(characterId, mapCode, itemId))
            }
            post("/chest") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                call.respondWithHero(campaign.openChest(characterId, mapCode))
            }
            // 0.46.0: осквернённая зона - случайный портал за заход, не больше одного, своя таблица добычи.
            post("/corrupt") {
                val characterId = call.queryParam("characterId")
                val mapCode = call.queryParam("mapCode")
                val monsterCode = call.queryParam("monsterCode")
                call.respondWithHero(campaign.corrupt(characterId, mapCode, monsterCode))
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
                // Вариант мастерства или атрибутного узла (с 0.52.0); у прочих узлов его нет
                val choice = call.request.queryParameters["choice"]?.let { it.toIntOrNull() ?: throw SkillTreeExceptions.funExceptionChoice("allocate", it) }
                val data = repo.allocateSkillNode(characterId, nodeCode, choice)
                call.respondWithHero(data)
            }
            post("/refund") {
                val characterId = call.queryParam("characterId")
                val nodeCode = call.queryParam("nodeCode")
                val data = repo.refundSkillNode(characterId, nodeCode)
                call.respondWithHero(data)
            }
            post("/reset") {
                val characterId = call.queryParam("characterId")
                val data = repo.resetSkillTree(characterId)
                call.respondWithHero(data)
            }
        }
    }
}