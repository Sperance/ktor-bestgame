package features.userMongo

import base.exception.ExceptionForCode
import base.repository.BaseRepositoryMongo
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import features.characterMongo.CharacterMongo
import features.characterMongo.CharacterMongoRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

object UserRepositoryMongo : BaseRepositoryMongo<UserMongo>(
    entityClass = UserMongo::class
) {
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

    override suspend fun validateBeforeInsert(entity: UserMongo) {
        if (!entity.email.contains("@")) throw ExceptionForCode("'${entity.name}' has invalid e-mail: ${entity.email}", "UMR_VALIDATEINSERT_EMAIL")
        if (entity.age !in 12..120) throw ExceptionForCode("'${entity.name}' has invalid age: ${entity.age}", "UMR_VALIDATEINSERT_AGE")
        if (entity.password.length < 6) throw ExceptionForCode("'${entity.name}' has invalid password length: ${entity.password.length}", "UMR_VALIDATEINSERT_PASSWORD")
        if (entity.salt != "") throw ExceptionForCode("Field 'salt' has blocked to modify", "UMR_VALIDATEINSERT_SALT")
        if (findByLogin(entity.login) != null) throw ExceptionForCode("Login is exists! Please choose another one.", "UMR_VALIDATEINSERT_LOGIN_DUPLICATE")
        if (findByEmail(entity.email) != null) throw ExceptionForCode("Email is exists! Please choose another one.", "UMR_VALIDATEINSERT_EMAIL_DUPLICATE")

        checkPassword(entity.password)
        generatePassword(entity)
    }

    override suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        changes["email"]?.let { email ->
            val emailStr = email as? String ?: ""
            if (emailStr.isNotEmpty() && !emailStr.contains("@")) {
                throw ExceptionForCode("Invalid e-mail: $emailStr", "UMR_VALIDATEUPDATE_EMAIL")
            }
        }

        changes["age"]?.let { age ->
            val ageInt = when (age) {
                is Int -> age
                is Long -> age.toInt()
                is String -> age.toIntOrNull()
                else -> null
            }
            if (ageInt != null && ageInt !in 12..120) {
                throw ExceptionForCode("Invalid age: $ageInt", "UMR_VALIDATEUPDATE_AGE")
            }
        }

        changes["password"]?.let { password ->
            val passwordStr = password as? String ?: ""
            if (passwordStr.isNotEmpty() && passwordStr.length < 6) {
                throw ExceptionForCode("Invalid password length: ${passwordStr.length}", "UMR_VALIDATEUPDATE_PASSWORD")
            }
        }

        if (changes.containsKey("salt"))
            throw ExceptionForCode("Field 'salt' has blocked to modify", "UMR_VALIDATEINSERT_SALT")
    }

    override suspend fun validateAfterDelete(entity: UserMongo, session: ClientSession, softDelete: Boolean) {
        val characters = CharacterMongoRepository.findByFieldList(CharacterMongo::userId, entity.getId())
        characters.forEach { char ->
            if (softDelete) {
                CharacterMongoRepository.softDelete(char, session)
            } else {
                CharacterMongoRepository.deleteById(char, session)
            }
        }
    }

    private fun generatePassword(entity: UserMongo) {
        entity.salt = generateSalt()
        entity.password = hashPassword(entity.password, entity.salt)
    }

    private fun checkPassword(password: String) {
        if (password.isEmpty()) throw ExceptionForCode("Пароль не может быть пустым", "UMR_CHECKPASS_EMPTY")
        if (password.length !in 6..64) throw ExceptionForCode("Пароль должен содержать не менее 6 и не более 64 символов", "UMR_CHECKPASS_LENGTH")
        if (password.none { it.isDigit() }) throw ExceptionForCode("Пароль должен содержать хотя бы 1 цифру", "UMR_CHECKPASS_DIGIT")
        if (password.none { it.isUpperCase() }) throw ExceptionForCode("Пароль должен содержать хотя бы 1 заглавную букву", "UMR_CHECKPASS_UPPERCASE")
        if (password.contains(" ")) throw ExceptionForCode("Пароль не должен содержать пробелы", "UMR_CHECKPASS_PROBEL")
    }

    /**********/

    suspend fun findByEmail(email: String): UserMongo? {
        return findByField(UserMongo::email, email)
    }

    suspend fun searchByName(name: String): List<UserMongo> {
        return findByFieldList(UserMongo::name, name)
    }

    suspend fun findActive(): List<UserMongo> {
        return findByFieldList(UserMongo::isActive, true)
    }

    suspend fun findByLogin(login: String): UserMongo? {
        return findByField(UserMongo::login, login)
    }

    suspend fun authenticate(login: String, password: String): UserMongo {
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
            throw ExceptionForCode("Не найден пользователь с id $id", "UMR_CHANGEPASS_NOTUSER")
        }

        if (user.password != hashPassword(password, user.salt)) {
            throw ExceptionForCode("Неверный пароль пользователя ${user.login}", "UMR_CHANGEPASS_PASSWORD")
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