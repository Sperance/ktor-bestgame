package features.data.inventory

import application.enums.EnumEquipmentType
import base.route.ApiMongoResponse
import base.route.BaseRoute
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import features.logic.bench.CraftingBench
import features.logic.currency.CurrencyOutcome
import kotlinx.serialization.Serializable

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
            // Необязательный слот - какое из двух колец занять
            val slot = call.request.queryParameters["slot"]?.let { name -> EnumEquipmentType.entries.firstOrNull { it.name == name } }
            val data = repo.equip(characterId, inventoryId, slot)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/applyOrb") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val orbItemId = call.queryParam("orbItemId")
            val outcome = repo.applyOrb(characterId, inventoryId, orbItemId)
            call.respond(ApiMongoResponse.ok(CurrencyApplyResponse.of(outcome)))
        }
        get("/bench") {
            call.respond(ApiMongoResponse.ok(CraftingBench.recipes))
        }
        post("/craft") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val recipe = call.queryParam("recipe")
            call.respond(ApiMongoResponse.ok(CurrencyApplyResponse.of(repo.craft(characterId, inventoryId, recipe))))
        }
        post("/uncraft") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            call.respond(ApiMongoResponse.ok(CurrencyApplyResponse.of(repo.uncraft(characterId, inventoryId))))
        }
        post("/socket") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val nodeCode = call.queryParam("nodeCode")
            val data = repo.socket(characterId, inventoryId, nodeCode)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/unsocket") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val data = repo.unsocket(characterId, inventoryId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/sell") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val data = repo.sellForGold(characterId, inventoryId)
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

/**
 * Ответ на применение валютной сферы и на работу верстака.
 *
 * Текста здесь нет: клиент собирает фразу сам по [messageKey] и [messageArgs],
 * см. [features.logic.currency.CurrencyOutcome].
 *
 * @property created предмет, который сфера создала (Mirror of Kalandra)
 */
@Serializable
data class CurrencyApplyResponse(
    val messageKey: String,
    val messageArgs: List<String>,
    val item: CharacterEquipment,
    val created: CharacterEquipment? = null,
) {
    companion object {
        fun of(outcome: CurrencyOutcome) = CurrencyApplyResponse(outcome.messageKey, outcome.messageArgs, outcome.item, outcome.created)
    }
}
