package features.logic.skilltree

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.SkillTreeCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SkillTreeNodeRoute(repo: SkillTreeNodeRepository) : BaseRoute<SkillTreeNode, SkillTreeNode>(
    repository = repo,
    entitySerializer = SkillTreeNode.serializer(),
    responseSerializer = SkillTreeNode.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: SkillTreeCache by inject()

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
        get("/starts") {
            val data = cache.startNodes()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
