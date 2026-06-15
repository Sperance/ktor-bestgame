package mongo_test

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking

class UserRepositoryMongo(database: MongoDatabase) : BaseRepositoryMongo<UserMongo>(
    database = database,
    collectionName = "UserMongo",
    entityClass = UserMongo::class
) {
    init {
        runBlocking {
            initialize(
                uniqueIndexes = listOf(
                    UniqueIndexConfig(
                        indexName = "idx_unique_email",
                        fields = listOf("email")
                    )
                )
            )
        }
    }
}