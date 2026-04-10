package features.characters

import application.model.ItemStock
import base.exception.NotFoundException
import base.route.BaseRoute
import features.items.ItemsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonObject

class CharacterRoute(
    val characterService: CharacterService = CharacterService(),
    val itemService: ItemsService = ItemsService()
) : BaseRoute<Character, CharacterTable>(
    service = characterService,
    basePath = "/api/character",
    entitySerializer = Character.serializer()   // ← явный сериализатор
) {
    override fun additionalRoutes(route: Route) = with(route) {

        /** Добавить предмет в инвентарь персонажа */
        post("/inventory/{characterId}/add") {

            val charId = call.longParam("characterId")
            val item = call.receive<ItemStock>()

            if (!characterService.exists(charId)) {
                throw NotFoundException("character(id=${charId}) not found")
            }

            if (!itemService.exists(item.item_id)) {
                throw NotFoundException("Item(id=${item.item_id}) not found")
            }

            characterService.addItemToInventory(charId, item)

            call.respond(HttpStatusCode.OK)
        }
    }
}