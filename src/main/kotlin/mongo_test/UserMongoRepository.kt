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

    override suspend fun validateBeforeInsert(entity: UserMongo) {
        if (!entity.email.contains("@")) throw IllegalArgumentException("'${entity.name}' has invalid e-mail: ${entity.email}")
        if (entity.age !in 12..120) throw IllegalArgumentException("'${entity.name}' has invalid age: ${entity.age}")
    }
}