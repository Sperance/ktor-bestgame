package features.data.skilltree

import base.route.ApiMongoResponse
import base.route.BaseRoute
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

class CharacterSkillNodeRoute(
    val repo: CharacterSkillNodeRepository
) : BaseRoute<CharacterSkillNode, CharacterSkillNode>(
    repository = repo,
    entitySerializer = CharacterSkillNode.serializer(),
    responseSerializer = CharacterSkillNode.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/byCharacter") {
            val characterId = call.queryParam("characterId")
            val data = repo.stateOf(characterId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/allocate") {
            val characterId = call.queryParam("characterId")
            val nodeCode = call.queryParam("nodeCode")
            val data = repo.allocate(characterId, nodeCode)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/refund") {
            val characterId = call.queryParam("characterId")
            val nodeCode = call.queryParam("nodeCode")
            val data = repo.refund(characterId, nodeCode)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/reset") {
            val characterId = call.queryParam("characterId")
            val data = repo.reset(characterId)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
