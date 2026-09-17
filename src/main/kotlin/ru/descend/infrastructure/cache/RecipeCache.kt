package ru.descend.infrastructure.cache

import ru.descend.features.recipe.model.Recipe
import ru.descend.features.recipe.persistence.RecipeRepository

class RecipeCache(repository: RecipeRepository) : MongoCache<Recipe, RecipeRepository>(repository)
