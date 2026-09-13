package ru.descend.features.redemptioncodes.http

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.core.component.KoinComponent
import ru.descend.features.redemptioncodes.model.RedemptionCodes
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.shared.http.ApiMongoResponse
import ru.descend.shared.http.BaseRoute

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
