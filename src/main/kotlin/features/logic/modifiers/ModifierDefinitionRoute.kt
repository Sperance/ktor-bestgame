package features.logic.modifiers

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.ModifierDefinitionCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierDefinitionRoute(repo: ModifierDefinitionRepository) : BaseRoute<ModifierDefinition, ModifierDefinition>(
    repository = repo,
    entitySerializer = ModifierDefinition.serializer(),
    responseSerializer = ModifierDefinition.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: ModifierDefinitionCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = cache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
        get("/byCode") {
            val code = call.queryParam("code")
            val data = cache.findByCode(code)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
