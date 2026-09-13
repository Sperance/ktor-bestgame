package ru.descend.features.recipe.http

import io.ktor.server.routing.*
import ru.descend.features.recipe.model.Recipe
import ru.descend.features.recipe.persistence.RecipeRepository
import ru.descend.shared.http.BaseRoute

class RecipeRoute(val repo: RecipeRepository) : BaseRoute<Recipe, Recipe>(repo, Recipe.serializer(), Recipe.serializer(), { it })
