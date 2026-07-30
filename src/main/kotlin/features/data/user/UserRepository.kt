package features.data.user

import base.exception.model.UserExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.character.Character
import features.data.character.CharacterRepository
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.getValue

class UserRepository : BaseRepository<User>(
    entityClass = User::class
), KoinComponent {
    val characterRepository: CharacterRepository by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_email",
                fields = listOf("email")
            ),
            UniqueIndexConfig(
                indexName = "idx_unique_login",
                fields = listOf("login")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: User, session: ClientSession) {
        if (!entity.email.contains("@")) throw UserExceptions.funExceptionInvalidEmail("validateBeforeInsert", entity.email)
        if (entity.age !in 12..120) throw UserExceptions.funExceptionInvalidAge("validateBeforeInsert", entity.age.toString())
        if (entity.password.length < 6) throw UserExceptions.funExceptionInvalidPassword("validateBeforeInsert", entity.password)
        if (entity.salt != "") throw UserExceptions.funExceptionSalt("validateBeforeInsert")
        if (findByLogin(entity.login) != null) throw UserExceptions.funExceptionLoginExists("validateBeforeInsert", entity.login)
        if (findByEmail(entity.email) != null) throw UserExceptions.funExceptionEmailExists("validateBeforeInsert", entity.email)

        checkPassword(entity.password)
        generatePassword(entity)
    }

    override suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        changes["email"]?.let { email ->
            val emailStr = email as? String ?: ""
            if (emailStr.isNotEmpty() && !emailStr.contains("@")) {
                throw UserExceptions.funExceptionInvalidEmail("validateBeforeUpdate", email.toString())
            }
        }

        changes["age"]?.let { age ->
            val ageInt = when (age) {
                is Int -> age
                is Long -> age.toInt()
                is String -> age.toIntOrNull()
                else -> null
            }
            if (ageInt == null || ageInt !in 12..120) {
                throw UserExceptions.funExceptionInvalidAge("validateBeforeUpdate", age.toString())
            }
        }

        changes["password"]?.let { password ->
            val passwordStr = password as? String ?: ""
            if (passwordStr.isNotEmpty() && passwordStr.length < 6) {
                throw UserExceptions.funExceptionPasswordCheck("validateBeforeUpdate", password.toString())
            }
        }

        if (changes.containsKey("salt"))
            throw UserExceptions.funExceptionSalt("validateBeforeUpdate")
    }

    override suspend fun validateAfterDelete(entity: User, session: ClientSession, softDelete: Boolean) {
        val characters = characterRepository.findByFieldList(Character::userId, entity.getId())
        characters.forEach { char ->
            if (softDelete) {
                characterRepository.softDelete(char, session)
            } else {
                characterRepository.deleteById(char, session)
            }
        }
    }

    private fun generatePassword(entity: User) {
        entity.salt = generateSalt()
        entity.password = hashPassword(entity.password, entity.salt)
    }

    private fun checkPassword(password: String) {
        if (password.isEmpty()) throw UserExceptions.funExceptionPasswordEmpty("checkPassword")
        if (password.length !in 6..64) throw UserExceptions.funExceptionPasswordLength("checkPassword")
        if (password.none { it.isDigit() }) throw UserExceptions.funExceptionPasswordOneDigit("checkPassword")
        if (password.none { it.isUpperCase() }) throw UserExceptions.funExceptionPasswordOneUppercase("checkPassword")
        if (password.contains(" ")) throw UserExceptions.funExceptionPasswordWhitespace("checkPassword")
    }

    suspend fun findByEmail(email: String): User? {
        return findByField(User::email, email)
    }

    suspend fun searchByName(name: String): List<User> {
        return findByFieldList(User::name, name)
    }

    suspend fun findActive(): List<User> {
        return findByFieldList(User::isActive, true)
    }

    suspend fun findByLogin(login: String): User? {
        return findByField(User::login, login)
    }

    suspend fun authenticate(login: String, password: String): User {
        val credentials = findCredentialsByLogin(login)
            ?: throw UserExceptions.funExceptionPasswordLoginPass("authenticate")

        val (userId, storedHash, storedSalt) = credentials

        if (hashPassword(password, storedSalt) != storedHash) {
            throw UserExceptions.funExceptionPasswordLoginPass("authenticate")
        }

        val user = findById(userId)

        if (user == null) {
            throw UserExceptions.funExceptionPasswordLoginPass("authenticate")
        }

        if (!user.isActive) {
            throw UserExceptions.funExceptionInactive("authenticate", user.login)
        }

        return transactionExecute("User authenticate") { session ->
            updateFields(user, mapOf("lastLoginDate" to LocalDateTime.now()), session)
        }
    }

    /**
     * Возвращает (id, password_hash, salt) по login напрямую из БД,
     * минуя toEntity (который маскирует @WriteOnly-поля).
     *
     * Используется для аутентификации.
     */
    suspend fun findCredentialsByLogin(login: String): Triple<String, String, String>? {

        val result = findByLogin(login)

        if (result == null) return null

        return Triple(result.getId(), result.password, result.salt)
    }

    suspend fun changePassword(id: String, password: String, newPassword: String): String {
        val user = findById(id)
        if (user == null) {
            throw UserExceptions.funExceptionFoundUserId("changePassword", id)
        }

        if (user.password != hashPassword(password, user.salt)) {
            throw UserExceptions.funExceptionPasswordLoginPass("changePassword", user.login)
        }

        checkPassword(newPassword)

        val newHashedPass = hashPassword(newPassword, user.salt)
        transactionExecute { session ->
            updateFields(user, mapOf("password" to newHashedPass), session)
        }
        return "Success"
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