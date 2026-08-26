package features.data.recipe

import base.exception.model.RecipeExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.RecipeCache
import features.data.items.ItemsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeRepository : BaseRepository<Recipe>(entityClass = Recipe::class), KoinComponent {
    private val cache: RecipeCache by inject()
    private val repoItems: ItemsRepository by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            ),
        ))
    }

    override suspend fun validateBeforeInsert(entity: Recipe, session: ClientSession) {
        // 1. Проверки на null
        if (entity.arrayIn.any { it.itemType.getFirstCorrect() == null })
            throw RecipeExceptions.funExceptionItemInNull("validateBeforeInsert")
        if (entity.arrayOut.any { it.itemType.getFirstCorrect() == null })
            throw RecipeExceptions.funExceptionItemOutNull("validateBeforeInsert")
        if (entity.arrayOut.any { it.itemType.name == null })
            throw RecipeExceptions.funExceptionOutIncorrect("validateBeforeInsert")

        // 2. Собираем условия (поле → значение) для каждого RecipeParam
        val conditions = mutableListOf<Pair<String, String>>()

        fun addConditions(params: List<RecipeParam>) {
            params.forEach { param ->
                val type = param.itemType
                val value = type.getFirstCorrect()!! // уже проверено на null
                val field = when {
                    type.name != null -> "name"
                    type.subCategory != null -> "subCategory"
                    else -> "category"
                }
                conditions.add(field to value)
            }
        }

        addConditions(entity.arrayIn)
        addConditions(entity.arrayOut)

        // 3. Строим OR-запрос по всем условиям
        val filters = conditions.map { (field, value) ->
            Filters.eq("type.$field", value)
        }
        val items = repoItems.findByFilter(Filters.or(filters))

        // 4. Проверяем, что для КАЖДОГО условия есть документ с таким полем и значением
        val missing = conditions.filter { (field, value) ->
            items.none { item ->
                when (field) {
                    "name" -> item.type.name == value
                    "subCategory" -> item.type.subCategory == value
                    "category" -> item.type.category == value
                    else -> false
                }
            }
        }

        if (missing.isNotEmpty()) {
            val details = missing.joinToString { "${it.first}=${it.second}" }
            throw RecipeExceptions.funExceptionItemNotFound("validateBeforeInsert", details)
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
}