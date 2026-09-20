package features.logic.progression

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterClassRoute(repo: CharacterClassRepository) : BaseRoute<CharacterClass, CharacterClass>(
    repository = repo,
    entitySerializer = CharacterClass.serializer(),
    responseSerializer = CharacterClass.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: CharacterClassCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/byCode") {
            val code = call.queryParam("code")
            call.respond(ApiMongoResponse.ok(cache.findByCode(code)))
        }
    }
}

class ExperienceLevelRoute(repo: ExperienceLevelRepository) : BaseRoute<ExperienceLevel, ExperienceLevel>(
    repository = repo,
    entitySerializer = ExperienceLevel.serializer(),
    responseSerializer = ExperienceLevel.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: ExperienceLevelCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/byExperience") {
            val experience = call.queryParam("experience", 0.0)
            call.respond(ApiMongoResponse.ok(cache.levelOf(experience)))
        }
    }
}
