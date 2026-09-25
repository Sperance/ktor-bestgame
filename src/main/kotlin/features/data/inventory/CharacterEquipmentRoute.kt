package features.data.inventory

import features.logic.hero.respondWithHero
import application.enums.EnumEquipmentType
import base.route.BaseRoute
import base.route.characterId
import base.route.inventoryId
import base.route.optionalParam
import base.route.queryParam
import base.route.respondOk
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import features.logic.currency.CurrencyOutcome
import kotlinx.serialization.Serializable

class CharacterEquipmentRoute(
    private val repo: CharacterEquipmentRepository
) : BaseRoute<CharacterEquipment>(
    repository = repo,
    entitySerializer = CharacterEquipment.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        post("/equip") {
            // Необязательный слот - какое из двух колец занять
            val slot = call.optionalParam("slot")?.let { name -> EnumEquipmentType.entries.firstOrNull { it.name == name } }
            call.respondWithHero(repo.equip(call.characterId, call.inventoryId, slot))
        }
        post("/unequip") {
            call.respondWithHero(repo.unequip(call.characterId, call.inventoryId))
        }
        post("/applyOrb") {
            call.respondWithHero(CurrencyApplyResponse.of(repo.applyOrb(call.characterId, call.inventoryId, call.queryParam("orbItemId"))))
        }
        get("/bench") {
            call.respondOk(repo.bench(call.characterId))
        }
        post("/craft") {
            call.respondWithHero(CurrencyApplyResponse.of(repo.craft(call.characterId, call.inventoryId, call.queryParam("recipe"))))
        }
        post("/uncraft") {
            call.respondWithHero(CurrencyApplyResponse.of(repo.uncraft(call.characterId, call.inventoryId)))
        }
        post("/socket") {
            call.respondWithHero(repo.socket(call.characterId, call.inventoryId, call.queryParam("nodeCode")))
        }
        post("/unsocket") {
            call.respondWithHero(repo.unsocket(call.characterId, call.inventoryId))
        }
        post("/sell") {
            call.respondWithHero(repo.sellForGold(call.characterId, call.inventoryId))
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
