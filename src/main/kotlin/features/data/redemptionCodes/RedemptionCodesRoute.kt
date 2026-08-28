package features.data.redemptionCodes

import base.route.ApiMongoResponse
import base.route.BaseRoute
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.core.component.KoinComponent

class RedemptionCodesRoute(val repo: RedemptionCodesRepository) : BaseRoute<RedemptionCodes, RedemptionCodes>(
    repository = repo,
    entitySerializer = RedemptionCodes.serializer(),
    responseSerializer = RedemptionCodes.serializer(),
    toResponse = { it }
), KoinComponent {
    override fun additionalRoutes(route: Route) = with(route) {
        post("useRedeptionCode") {
            val characterId = call.queryParam("characterId")
            val redemptionCode = call.queryParam("redemptionCode")
            val data = repo.useCharacterRedemptionCode(characterId, redemptionCode)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}