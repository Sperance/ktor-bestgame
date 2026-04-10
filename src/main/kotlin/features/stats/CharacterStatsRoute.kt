package features.stats

import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class CharacterStatsRoute(
    private val statsService: CharacterStatsService = CharacterStatsService()
) : BaseRoute<CharacterStats, CharacterStatsTable>(
    service = statsService,
    basePath = "/api/stats",
    entitySerializer = CharacterStats.serializer()
) {

    override fun additionalRoutes(route: Route) = with(route) {

        /** Получить статистику персонажа (создаёт пустую, если ещё нет) */
        get("/character/{characterId}") {
            val charId = call.longParam("characterId")
            val stats = statsService.getByCharacter(charId)
            call.respondEntity(stats)
        }
    }
}
