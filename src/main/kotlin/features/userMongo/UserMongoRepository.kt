package features.userMongo

import base.exception.ExceptionForCode
import config.MongoFactory
import base.repository.BaseRepositoryMongo
import base.repository.UniqueIndexConfig
import config.MongoFactory.transactionExecute
import extensions.printLog
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

class UserRepositoryMongo : BaseRepositoryMongo<UserMongo>(
    database = MongoFactory.db,
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
        if (!entity.email.contains("@")) throw ExceptionForCode("'${entity.name}' has invalid e-mail: ${entity.email}", "UMR_VALIDATEINSERT_EMAIL")
        if (entity.age !in 12..120) throw ExceptionForCode("'${entity.name}' has invalid age: ${entity.age}", "UMR_VALIDATEINSERT_AGE")
        if (entity.password.length < 6) throw ExceptionForCode("'${entity.name}' has invalid password length: ${entity.password.length}", "UMR_VALIDATEINSERT_PASSWORD")
        if (entity.salt != "") throw ExceptionForCode("Field 'salt' has blocked to modify", "UMR_VALIDATEINSERT_SALT")
        if (findByLogin(entity.login) != null) throw ExceptionForCode("Login is exists! Please choose another one.", "UMR_VALIDATEINSERT_LOGIN_DUPLICATE")
        if (findByEmail(entity.email) != null) throw ExceptionForCode("Email is exists! Please choose another one.", "UMR_VALIDATEINSERT_EMAIL_DUPLICATE")

        entity.salt = generateSalt()
        printLog("Password stock: ${entity.password}")
        entity.password = hashPassword(entity.password, entity.salt)
        printLog("Generated salt: ${entity.salt}")
        printLog("Password hash: ${entity.password}")
    }

    override suspend fun validateBeforeUpdate(entity: UserMongo) {
        if (entity.email != "" && !entity.email.contains("@")) throw ExceptionForCode("'${entity.name}' has invalid e-mail: ${entity.email}", "UMR_VALIDATEINSERT_EMAIL")
        if (entity.age != null && entity.age !in 12..120) throw ExceptionForCode("'${entity.name}' has invalid age: ${entity.age}", "UMR_VALIDATEINSERT_AGE")
        if (entity.password != "" && entity.password.length < 6) throw ExceptionForCode("'${entity.name}' has invalid password length: ${entity.password.length}", "UMR_VALIDATEINSERT_PASSWORD")
        if (entity.salt != "") throw ExceptionForCode("Field 'salt' has blocked to modify", "UMR_VALIDATEINSERT_SALT")
    }

    /**********/

    suspend fun findByEmail(email: String): UserMongo? {
        return findOneByFilter {
            UserMongo::email eq email
        }
    }

    suspend fun searchByName(name: String): List<UserMongo> {
        return findByFilter {
            UserMongo::name eq name
        }
    }

    suspend fun findActive(): List<UserMongo> {
        return findByFilter {
            UserMongo::isActive eq true
        }
    }

    suspend fun findByLogin(login: String): UserMongo? {
        return findOneByFilter {
            UserMongo::login eq login
        }
    }

    suspend fun authenticate(login: String, password: String): UserMongoResponse {
        val credentials = findCredentialsByLogin(login)
            ?: throw ExceptionForCode("Invalid login or password", "UMR_AUTH_INVALID")

        val (userId, storedHash, storedSalt) = credentials

        if (hashPassword(password, storedSalt) != storedHash) {
            throw ExceptionForCode("Invalid login or password", "UMR_AUTH_WRONG_PASSWORD")
        }

        val user = findById(userId)

        if (user == null) {
            throw ExceptionForCode("User not found", "UMR_AUTH_USER_NOT_FOUND")
        }

        if (!user.isActive) {
            throw ExceptionForCode("Account is deactivated", "UMR_AUTH_DEACTIVATED")
        }

        return transactionExecute("User authenticate") { session ->
            updateFields(user, mapOf("lastLoginDate" to LocalDateTime.now()), session, validateBeforeUpdate = false)
        }.toResponse()
    }

    /**
     * Возвращает (id, password_hash, salt) по login напрямую из БД,
     * минуя toEntity (который маскирует @WriteOnly-поля).
     *
     * Используется для аутентификации.
     */
    suspend fun findCredentialsByLogin(login: String): Triple<String, String, String>? {

        val result = findOneByFilter {
            UserMongo::login eq login
        }

        if (result == null) return null

        return Triple(result.id(), result.password, result.salt)
    }


    // ==================== Password utils ====================

    private fun generateSalt(length: Int = 32): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "$salt:$password"
        val hash = digest.digest(salted.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}