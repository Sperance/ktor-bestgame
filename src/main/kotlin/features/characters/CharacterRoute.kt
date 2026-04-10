package features.characters

import base.exception.NotFoundException
import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

class CharacterRoute(
    val characterService: CharacterService = CharacterService()
) : BaseRoute<Character, CharacterTable>(
    service = characterService,
    basePath = "/api/character",
    entitySerializer = Character.serializer()   // ← явный сериализатор
) {
    override fun additionalRoutes(route: Route) = with(route) {

        /** Получить статистику персонажа (создаёт пустую, если ещё нет) */
        post("/inventory/{characterId}/add") {

            val charId = call.longParam("characterId")

            val character = characterService.findById(charId)
                ?: throw NotFoundException("Character with id $charId not found")

//            character.inventory.add()

            call.respondEntity(character)
        }
    }
}