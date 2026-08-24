package features.data.recipe

import base.exception.model.RecipeExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.toObjectId
import features.caches.RecipeCache
import features.data.items.ItemsRepository
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeRepository : BaseRepository<Recipe>(entityClass = Recipe::class), KoinComponent {
    private val cache: RecipeCache by inject()
    private val repoItems: ItemsRepository by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_all",
                fields = listOf("name", "arrayIn", "arrayOut")
            ),
        ))
    }

    override suspend fun validateBeforeInsert(entity: Recipe, session: ClientSession) {
        val listItemsID = mutableSetOf<ObjectId>()
        listItemsID.addAll(entity.arrayIn.map { it.item.toObjectId() })
        listItemsID.addAll(entity.arrayOut.map { it.item.toObjectId() })
        val items = repoItems.findByFilter(Filters.`in`("_id", listItemsID))
        val foundIds = items.map { it._id }.toSet()
        val missingIds = listItemsID - foundIds
        if (missingIds.isNotEmpty())
            throw RecipeExceptions.funExceptionItemNotFound("validateBeforeInsert", missingIds.toString())

        if (findByField(Recipe::name, entity.name) != null)
            throw RecipeExceptions.funExceptionDuplicateName("validateBeforeInsert", entity.name)
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