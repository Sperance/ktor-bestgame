package features.data.user

import base.exception.model.UserExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.now
import kotlinx.datetime.LocalDateTime
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import features.logic.auth.Passwords

class UserRepository : BaseRepository<User>(User::class) {
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
        if (entity.password.length < 6) throw UserExceptions.funExceptionInvalidPassword("validateBeforeInsert")
        if (entity.salt != "") throw UserExceptions.funExceptionSalt("validateBeforeInsert", "salt")
        // Уникальность проверяется и по мягко удалённым: их документы никуда
        // не делись, и уникальный индекс всё равно не даст занять логин или почту
        if (findByLogin(entity.login, includeDeleted = true) != null) throw UserExceptions.funExceptionLoginExists("validateBeforeInsert", entity.login)
        if (findByEmail(entity.email, includeDeleted = true) != null) throw UserExceptions.funExceptionEmailExists("validateBeforeInsert", entity.email)

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

        // Пароль, соль и идентификатор устройства - это ключи от аккаунта. Общий PUT записал
        // бы пароль как есть, без хеша, а device_id - это вход без пароля; меняются они только
        // своими маршрутами.
        listOf("password", "salt", "device_id").forEach { field ->
            if (changes.containsKey(field)) throw UserExceptions.funExceptionSalt("validateBeforeUpdate", field)
        }
    }

    private fun generatePassword(entity: User) {
        // Соль и число итераций живут внутри строки хеша, поле salt нужно только старым хешам.
        entity.password = Passwords.hash(entity.password)
        entity.salt = ""
    }

    private fun checkPassword(password: String) {
        if (password.isEmpty()) throw UserExceptions.funExceptionPasswordEmpty("checkPassword")
        if (password.length !in 6..64) throw UserExceptions.funExceptionPasswordLength("checkPassword")
        if (password.none { it.isDigit() }) throw UserExceptions.funExceptionPasswordOneDigit("checkPassword")
        if (password.none { it.isUpperCase() }) throw UserExceptions.funExceptionPasswordOneUppercase("checkPassword")
        if (password.contains(" ")) throw UserExceptions.funExceptionPasswordWhitespace("checkPassword")
    }

    /**
     * @param includeDeleted true - найдётся и мягко удалённый пользователь
     */
    suspend fun findByEmail(email: String, includeDeleted: Boolean = false): User? {
        return findByField(User::email, email, includeDeleted)
    }

    /**
     * @param includeDeleted true - найдётся и мягко удалённый пользователь
     */
    suspend fun findByLogin(login: String, includeDeleted: Boolean = false): User? {
        return findByField(User::login, login, includeDeleted)
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
        val credentials = findCredentialsByLogin(login)
            ?: throw UserExceptions.funExceptionPasswordLoginPass("authenticate")

        val (userId, storedHash, storedSalt) = credentials

        if (!Passwords.verify(password, storedHash, storedSalt)) {
            throw UserExceptions.funExceptionPasswordLoginPass("authenticate")
        }

        val user = findById(userId)
            ?: throw UserExceptions.funExceptionPasswordLoginPass("authenticate")

        if (!user.isActive) {
            throw UserExceptions.funExceptionInactive("authenticate", user.login)
        }

        // Хеш старого образца переписывается сразу, пока пароль в руках: другого случая
        // пересчитать его не будет, а сбрасывать пароли всем игрокам незачем.
        if (Passwords.needsRehash(storedHash)) storeHash(user._id, Passwords.hash(password))

        return transactionExecute("User authenticate") { session ->
            updateFields(user, mapOf("lastLoginDate" to LocalDateTime.now()), session)
        }
    }

    /**
     * Записывает хеш пароля в обход общего обновления: оно запрещает менять пароль и соль,
     * потому что через него пароль лёг бы в базу открытым текстом. Версия всё равно растёт -
     * чужая запись, начатая раньше, не перетрёт новый хеш.
     */
    private suspend fun storeHash(userId: String, hash: String) {
        collection.updateOne(
            Filters.eq("_id", userId),
            Updates.combine(Updates.set("password", hash), Updates.set("salt", ""), Updates.inc("version", 1L))
        )
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
        val user = findById(id)
            ?: throw UserExceptions.funExceptionFoundUserId("changePassword", id)

        if (!Passwords.verify(password, user.password, user.salt)) {
            throw UserExceptions.funExceptionPasswordLoginPass("changePassword", user.login)
        }

        checkPassword(newPassword)
        storeHash(user._id, Passwords.hash(newPassword))
        return "system.success"
    }

}
