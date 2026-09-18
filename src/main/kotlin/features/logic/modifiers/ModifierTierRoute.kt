package features.logic.modifiers

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.ModifierTierCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierTierRoute(repo: ModifierTierRepository) : BaseRoute<ModifierTier, ModifierTier>(
    repository = repo,
    entitySerializer = ModifierTier.serializer(),
    responseSerializer = ModifierTier.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: ModifierTierCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = cache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
        get("/byModifier") {
            val modifierId = call.queryParam("modifierId")
            val data = cache.findByModifier(modifierId)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
