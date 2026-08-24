package features.caches

import features.data.recipe.Recipe
import features.data.recipe.RecipeRepository

class RecipeCache(repository: RecipeRepository) : MongoCache<Recipe, RecipeRepository>(repository)