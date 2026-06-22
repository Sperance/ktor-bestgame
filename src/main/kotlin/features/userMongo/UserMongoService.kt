package features.userMongo

import base.exception.UnauthorizedException
import config.MongoFactory.transactionExecute
import features.BaseServiceMongo
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

class UserMongoService(
    private val repo: UserRepositoryMongo = UserRepositoryMongo()
) : BaseServiceMongo<UserMongo>(repo) {


    // ==================== Queries ====================

    suspend fun findByEmail(email: String) = repo.findByEmail(email)
    suspend fun findByLogin(login: String) = repo.findByLogin(login)
    suspend fun searchByName(name: String) = repo.searchByName(name)
    suspend fun findActive() = repo.findActive()

    // ==================== Auth ====================

    /**
     * Аутентификация пользователя по login + password.
     *
     * Проверяет:
     * 1. Существует ли пользователь с таким login
     * 2. Совпадает ли хеш пароля
     * 3. Активен ли аккаунт (isActive)
     *
     * @return User при успешной аутентификации (без password/salt — @WriteOnly)
     * @throws UnauthorizedException если логин/пароль неверны или аккаунт заблокирован
     */
    suspend fun authenticate(login: String, password: String): UserMongo {
        val credentials = repo.findCredentialsByLogin(login)
            ?: throw UnauthorizedException("Invalid login or password")

        val (userId, storedHash, storedSalt) = credentials

        if (hashPassword(password, storedSalt) != storedHash) {
            throw UnauthorizedException("Invalid login or password")
        }

        val user = findById(userId)

        if (user == null) {
            throw IllegalStateException("$login has no account")
        }

        if (!user.isActive) {
            throw UnauthorizedException("Account is deactivated")
        }

        return transactionExecute("[${repo.collectionName}::authenticate]") { session ->
            repository.updateFields(user, mapOf("lastLoginDate" to "${LocalDateTime.now()}"), session)
        }
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