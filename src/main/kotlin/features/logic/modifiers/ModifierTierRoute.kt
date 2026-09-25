package features.logic.modifiers

import base.route.BaseRoute
import base.route.queryParam
import base.route.respondOk
import features.caches.ModifierTierCache
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Тиры одного модификатора, из кеша; общего CRUD у тиров нет. */
class ModifierTierRoute(private val cache: ModifierTierCache) : BaseRoute<ModifierTier>(
    repository = cache.repository,
    entitySerializer = ModifierTier.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/byModifier") {
            call.respondOk(cache.findByModifier(call.queryParam("modifierId")))
        }
    }
}
