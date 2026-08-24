package features.data.recipe

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.RecipeCache
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeRoute(repo: RecipeRepository) : BaseRoute<Recipe, Recipe>(
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
    }

    override fun getAllRoute(route: Route): Route {
        return super.getAllRoute(route).describe {
            summary = "Получить все рецепты"
            parameters {
                query("id") {
                    description = "Если указан id, то вернется только рецепт с этим id. Если параметр не указан, то вернется список всех рецептов"
                    required = false
                }
            }
            responses {
                HttpStatusCode.OK {
                    description = "Успешно получили список рецептов (или 1 рецепт по ID)"
                    content {
                        schema = jsonSchema<Recipe>()
                    }
                }
            }
        }
    }
}