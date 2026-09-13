package ru.descend.features.user.persistence

import com.mongodb.kotlin.client.coroutine.ClientSession
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.character.model.Character
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.user.model.User
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.mongo.UniqueIndexConfig
import ru.descend.shared.error.model.UserExceptions
import ru.descend.shared.extensions.now

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
        if (!entity.login.matches(Regex("[A-Za-z0-9_.-]{3,64}"))) ru.descend.shared.http.invalid("Invalid login")
        if (entity.email.length > 254) ru.descend.shared.http.invalid("Invalid email")
        if (!entity.email.contains("@")) throw UserExceptions.funExceptionInvalidEmail("validateBeforeInsert", entity.email)
        if (entity.age !in 12..120) throw UserExceptions.funExceptionInvalidAge("validateBeforeInsert", entity.age.toString())
        checkPassword(entity.password)
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
                throw UserExceptions.funExceptionPasswordCheck("validateBeforeUpdate", "[redacted]")
            }
        }

        if (changes.containsKey("salt"))
            throw UserExceptions.funExceptionSalt("validateBeforeUpdate")
    }

    override suspend fun validateAfterDelete(entity: User, session: ClientSession, softDelete: Boolean) {
        val characters = characterRepository.findByFieldList(Character::userId, entity._id)
        characters.forEach { char ->
            if (softDelete) {
                characterRepository.softDelete(char, session)
            } else {
                characterRepository.deleteById(char, session)
            }
        }
    }

    private fun generatePassword(entity: User) {
        entity.password = ru.descend.infrastructure.security.PasswordHasher.hash(entity.password)
        entity.salt = ""
    }
    private fun checkPassword(password: String) = ru.descend.infrastructure.security.PasswordHasher.validate(password)

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

    suspend fun createByDevice(deviceId: String): User {
        if (deviceId.trim().isEmpty()) throw UserExceptions.funExceptionEmptyDevice("createByDevice")

        val findedUser = findByField(User::device_id, deviceId)
        if (findedUser != null) throw UserExceptions.funExceptionDoubleDevice("createByDevice", deviceId)

        val newUser = User()
        newUser.device_id = deviceId

        return transactionExecute("Create by Device_id") { session ->
            insert(newUser, session, false)
        }
    }

    suspend fun findByDeviceId(deviceId: String): User {
        if (deviceId.trim().isEmpty()) throw UserExceptions.funExceptionEmptyDevice("createByDevice")

        val user = findByField(User::device_id, deviceId)
        if (user == null) throw UserExceptions.funExceptionDeviceNotFound("createByDevice", deviceId)

        user.lastLoginDate = LocalDateTime.now()
        transactionExecute("Correct login date from DeviceId") { session ->
            update(user, session)
        }

        return user
    }

    suspend fun authenticate(login: String, password: String): User {
        if (login.length !in 1..100 || password.length !in 1..128) throw ru.descend.shared.http.ApiFailure(io.ktor.http.HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid credentials")
        val user = findByLogin(login)
        if (user == null || user.deleted || !user.isActive || !ru.descend.infrastructure.security.PasswordHasher.verify(password, user.password, user.salt))
            throw ru.descend.shared.http.ApiFailure(io.ktor.http.HttpStatusCode.Unauthorized, "INVALID_CREDENTIALS", "Invalid credentials")
        if (!user.password.startsWith("pbkdf2-sha256$")) {
            user.password = ru.descend.infrastructure.security.PasswordHasher.upgrade(password)
            user.salt = ""
            transactionExecute("password.migrate") { update(user, it) }
        }
        return user
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

        return Triple(result._id, result.password, result.salt)
    }

    suspend fun changePassword(id: String, password: String, newPassword: String): String {
        ru.descend.shared.http.invalid("Use the authenticated password command with expectedVersion")
    }
}
