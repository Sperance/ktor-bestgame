package features.data.character

import base.exception.model.SkillTreeExceptions
import base.route.BaseRoute
import base.route.Crud
import base.route.characterId
import base.route.mapCode
import base.route.queryParam
import base.route.respondOk
import features.data.character.character_data.CharacterItems
import features.logic.atlas.AtlasService
import features.logic.campaign.CampaignService
import features.logic.crafts.CraftsService
import features.logic.hero.HeroSnapshots
import features.logic.hero.respondWithHero
import features.logic.trade.MerchantService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

class CharacterRoute(
    private val repo: CharacterRepository,
    private val campaign: CampaignService,
    private val merchant: MerchantService,
    private val crafts: CraftsService,
    private val atlas: AtlasService,
) : BaseRoute<Character>(
    repository = repo,
    entitySerializer = Character.serializer(),
    operations = setOf(Crud.READ, Crud.COUNT, Crud.CREATE, Crud.UPDATE, Crud.DELETE),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        // Персонажи одного игрока: экран выбора при входе читает только их.
        get("/byUser") {
            call.respondOk(repo.findByUser(call.queryParam("userId")))
        }

        // 0.48.0: герой одним запросом - тот же снимок, что приходит в ответ команды, с ETag.
        get("/view") {
            val snapshot = HeroSnapshots.of(call.characterId, HeroSnapshots.known(call.request.headers[HeroSnapshots.HEADER]))
            val etag = "\"${snapshot.version}\""
            call.response.header(HttpHeaders.ETag, etag)
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified)
            else call.respondOk(snapshot)
        }

        route("/inventory") {
            get("/equipments") {
                call.respondOk(repo.getEquipmentsData(call.characterId))
            }
            get("/stats") {
                call.respondOk(repo.calculateStats(call.characterId))
            }
            get("/items") {
                // 0.37.0: добытое работой ложится в сумку до того, как её прочтут.
                crafts.settle(call.characterId)
                call.respondOk(repo.findById(call.characterId)?.parseItems())
            }
            post("/itemToInventory") {
                call.respondWithHero(repo.itemToInventory(call.characterId, call.queryParam("equipmentId")))
            }
            post("/experience") {
                call.respondWithHero(repo.addExperience(call.characterId, call.queryParam("amount", 0.0)))
            }
            post("/addItem") {
                call.respondWithHero(repo.addItem(call.characterId, call.receive<List<CharacterItems>>()))
            }
        }

        // Ремёсла (0.37.0): работа идёт на сервере по времени и досчитывается при каждом обращении.
        route("/crafts") {
            get {
                call.respondOk(crafts.state(call.characterId))
            }
            post("/start") {
                // 0.38.0: примеси кузнеца - коды через запятую.
                val additives = call.request.queryParameters["additives"]?.split(',').orEmpty()
                call.respondWithHero(crafts.start(call.characterId, call.queryParam("job"), additives))
            }
            post("/stop") {
                call.respondWithHero(crafts.stop(call.characterId))
            }
        }

        // Торговец (0.34.0): витрина героя раз в четыре часа и покупка с неё за золото.
        route("/merchant") {
            get {
                call.respondOk(merchant.stock(call.characterId))
            }
            post("/buy") {
                call.respondOk(merchant.buy(call.characterId, call.queryParam("offerId")))
            }
        }

        // Кампания (0.26.0): бой считает клиент по правилам сервера (0.28.0), добычу, опыт и цену смерти - сервер.
        route("/campaign") {
            get("/chapters") {
                call.respondOk(campaign.view())
            }
            get("/progress") {
                call.respondOk(campaign.progress(call.characterId))
            }
            post("/kill") {
                call.respondWithHero(campaign.kill(call.characterId, call.mapCode, call.queryParam("monsterCode"), call.queryParam("rarity"),
                    call.request.queryParameters["vaal"] == "true"))
            }
            post("/complete") {
                call.respondWithHero(campaign.complete(call.characterId, call.mapCode))
            }
            // 0.28.0: смерть героя стоит опыта по правилу сервера; уровень не падает.
            post("/fall") {
                call.respondWithHero(campaign.fall(call.characterId, call.mapCode))
            }
            // 0.31.0: сундуки - окно в шесть часов на карту у каждого героя, добыча - сервера.
            get("/chests") {
                call.respondOk(campaign.chests(call.characterId, call.mapCode))
            }
            post("/chest") {
                call.respondWithHero(campaign.openChest(call.characterId, call.mapCode))
            }
            // 0.32.0: босс карты - страж выхода, возвращается через час после смерти.
            get("/boss") {
                call.respondOk(campaign.boss(call.characterId, call.mapCode))
            }
            post("/boss") {
                call.respondWithHero(campaign.slayBoss(call.characterId, call.mapCode))
            }
            // 0.34.0: услуги карты за золото - ещё один сундук и вызов убитого стража.
            post("/treasure") {
                call.respondWithHero(campaign.treasure(call.characterId, call.mapCode))
            }
            post("/summon") {
                call.respondWithHero(campaign.summon(call.characterId, call.mapCode))
            }
            // 0.35.0: вход в локацию - с картой нужного уровня или без неё.
            post("/start") {
                call.respondWithHero(campaign.start(call.characterId, call.mapCode, call.request.queryParameters["itemId"]))
            }
            // 0.46.0: осквернённая зона - случайный портал за заход, не больше одного, своя таблица добычи.
            post("/corrupt") {
                call.respondWithHero(campaign.corrupt(call.characterId, call.mapCode, call.queryParam("monsterCode")))
            }
            // 0.57.0: портал ведёт в Ваал-зону - её модификаторы до входа, и закрытие без стража.
            post("/vaal") {
                call.respondOk(campaign.vaal(call.characterId, call.mapCode))
            }
            post("/vaal/leave") {
                call.respondWithHero(campaign.vaalLeave(call.characterId, call.mapCode))
            }
        }

        route("/skilltree") {
            get("/state") {
                call.respondOk(repo.skillTreeState(call.characterId))
            }
            post("/allocate") {
                // Вариант мастерства или атрибутного узла (с 0.52.0); у прочих узлов его нет
                val choice = call.request.queryParameters["choice"]?.let { it.toIntOrNull() ?: throw SkillTreeExceptions.funExceptionChoice("allocate", it) }
                call.respondWithHero(repo.allocateSkillNode(call.characterId, call.queryParam("nodeCode"), choice))
            }
            post("/refund") {
                call.respondWithHero(repo.refundSkillNode(call.characterId, call.queryParam("nodeCode")))
            }
            post("/reset") {
                call.respondWithHero(repo.resetSkillTree(call.characterId))
            }
        }

        // 0.60.0: пассивное дерево атласа - очки за выходы, редкие карты и стражей Ваал, откат за золото.
        route("/atlas") {
            get("/tree") {
                call.respondOk(atlas.tree())
            }
            get("/state") {
                call.respondOk(atlas.state(call.characterId))
            }
            post("/allocate") {
                call.respondWithHero(atlas.allocate(call.characterId, call.queryParam("nodeCode")))
            }
            post("/refund") {
                call.respondWithHero(atlas.refund(call.characterId, call.queryParam("nodeCode")))
            }
            post("/reset") {
                call.respondWithHero(atlas.reset(call.characterId))
            }
        }
    }
}
