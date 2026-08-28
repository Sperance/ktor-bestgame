package features.data.recipe

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.RecipeCache
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeRoute(val repo: RecipeRepository) : BaseRoute<Recipe, Recipe>(
    repository = repo,
    entitySerializer = Recipe.serializer(),
    responseSerializer = Recipe.serializer(),
    toResponse = { it }
), KoinComponent {
    private val cache: RecipeCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = cache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/useRecipe") {
            val characterId = call.queryParam("characterId")
            val recipeId = call.queryParam("recipeId")
            val recipeUse = call.receive<RecipeUse>()
            val data = repo.useRecipe(characterId, recipeId, recipeUse)
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}