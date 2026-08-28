package features.data.recipe

import base.exception.model.CharacterExceptions
import base.exception.model.RecipeExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.RecipeCache
import features.data.character.CharacterRepository
import features.data.items.ItemsRepository
import org.bson.conversions.Bson
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeRepository : BaseRepository<Recipe>(entityClass = Recipe::class), KoinComponent {
    private val cache: RecipeCache by inject()
    private val repoItems: ItemsRepository by inject()
    private val repoCharacters: CharacterRepository by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            ),
        ))
    }

    private fun validateNoDuplicatesInArrayIn(entity: Recipe) {
        val seen = mutableSetOf<Triple<String?, String?, String?>>()
        entity.arrayIn.forEach { param ->
            val key = Triple(param.category, param.subCategory, param.itemId)
            if (!seen.add(key)) {
                throw RecipeExceptions.funExceptionDuplicateInArrayIn("validateNoDuplicatesInArrayIn", "Duplicate entry: category=${param.category}, subCategory=${param.subCategory}, itemId=${param.itemId}")
            }
        }
    }

    override suspend fun validateBeforeInsert(entity: Recipe, session: ClientSession) {
        if (entity.arrayIn.any { it.countCorrect() == 0 })
            throw RecipeExceptions.funExceptionItemInNull("validateBeforeInsert")
        if (entity.arrayIn.any { it.countCorrect() > 1 })
            throw RecipeExceptions.funExceptionInMany("validateBeforeInsert")

        validateNoDuplicatesInArrayIn(entity)

        // ---------- Проверка arrayOut (только itemId) ----------
        val outIds = entity.arrayOut.map { it.itemId }.toSet()
        if (outIds.isNotEmpty()) {
            val foundOut = repoItems.findByFilter(Filters.`in`("_id", outIds))
            val foundOutIds = foundOut.map { it._id }.toSet()
            val missingOut = outIds - foundOutIds
            if (missingOut.isNotEmpty()) {
                throw RecipeExceptions.funExceptionItemNotFound("validateBeforeInsert", "Missing out items: $missingOut")
            }
        }

        // ---------- Проверка arrayIn (category + subCategory + itemId) ----------
        if (entity.arrayIn.isNotEmpty()) {
            // Строим список условий (OR) для каждой комбинации
            val inConditions = entity.arrayIn.map { param ->
                val filters = mutableListOf<Bson>()
                // Если itemId не null, добавляем условие по _id (преобразуем в ObjectId)
                param.itemId?.let { id ->
                    filters.add(Filters.eq("_id", id))
                }
                // Добавляем условия по категориям, если они не null
                param.category?.let { cat ->
                    filters.add(Filters.eq("category", cat))
                }
                param.subCategory?.let { sub ->
                    filters.add(Filters.eq("subCategory", sub))
                }
                // Если фильтров нет (все null) – такое условие означает "все документы",
                // но это исключительная ситуация, можно выбросить ошибку.
                if (filters.isEmpty()) {
                    throw RecipeExceptions.funExceptionItemInNull("validateBeforeInsert: all fields are null")
                }
                Filters.and(filters)
            }

            // Выполняем запрос с OR по всем условиям
            val foundIn = repoItems.findByFilter(Filters.or(inConditions))

            // Проверяем, что для каждого параметра есть совпадение среди найденных
            entity.arrayIn.forEach { param ->
                val exists = foundIn.any { item ->
                    // Сравниваем с учётом null (если поле null, то не проверяем)
                    (param.itemId == null || item._id == param.itemId) &&
                            (param.category == null || item.category == param.category) &&
                            (param.subCategory == null || item.subCategory == param.subCategory)
                }
                if (!exists) {
                    throw RecipeExceptions.funExceptionItemNotFound("validateBeforeInsert", "Item not found: id=${param.itemId}, category=${param.category}, subCategory=${param.subCategory}")
                }
            }
        }
    }

    override suspend fun validateAfterInsert(entity: Recipe, session: ClientSession) {
        cache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: Recipe, session: ClientSession, softDelete: Boolean) {
        cache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: Recipe, session: ClientSession) {
        cache.updateItem(entity)
    }

    suspend fun useRecipe(characterId: String, recipeId: String, recipeUse: RecipeUse): String {
        val character = repoCharacters.findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("useRecipe", characterId)

        val recipe = findById(recipeId)
        if (recipe == null) throw RecipeExceptions.funExceptionRecipeNotFound("useRecipe", recipeId)

        if (recipe.needOpenRecipe && !character.recipeAccess.contains(recipeId))
            throw RecipeExceptions.funExceptionRecipeNotAllowed("useRecipe", recipeId)

        val checkItems = repoItems.findByFilter(Filters.`in`("_id", recipeUse.ingridientsId))
        if (checkItems.size != recipeUse.ingridientsId.size)
            throw RecipeExceptions.funExceptionItemNotFound("useRecipe", (recipeUse.ingridientsId - checkItems.map { it._id }.toSet()).toString())

        //TODO Проверка что у персонажа достаточно ингредиентов

        return "Success"
    }
}