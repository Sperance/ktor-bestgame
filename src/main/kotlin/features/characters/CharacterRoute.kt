package features.characters

import application.model.ItemStock
import base.route.BaseRoute
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

class CharacterRoute(
    private val characterService: CharacterService = CharacterService(),
) : BaseRoute<Character, CharacterTable>(
    service = characterService,
    basePath = "/api/character",
    entitySerializer = Character.serializer()
) {

    override fun additionalRoutes(route: Route) = with(route) {

        /**
         * POST /api/character/{id}/inventory
         * Body: {"item_id": 5, "quantity": 10}
         *
         * Добавляет предмет в инвентарь персонажа.
         * Если предмет уже есть — увеличивает количество.
         */
        post("/{id}/inventory") {
            val characterId = call.longParam("id")
            val itemStock = call.receive<ItemStock>()
            val updated = characterService.addItemToInventory(characterId, itemStock)
            call.respondEntity(updated)
        }
    }
}