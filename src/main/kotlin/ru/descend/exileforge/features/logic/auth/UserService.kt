package ru.descend.exileforge.features.logic.auth

import kotlinx.datetime.LocalDateTime
import ru.descend.exileforge.application.enums.EnumUserRoles
import ru.descend.exileforge.base.exception.model.UserExceptions
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.features.data.user.TesterAccount
import ru.descend.exileforge.features.data.user.User
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.logic.auth.Passwords.offCpu

/** Аккаунт как вход: по паролю и по устройству, смена пароля, тестировщики администратора (1.69.0). */
class UserService(private val users: UserRepository) {
    /**
     * Аккаунт устройства (1.46.0): сервер сам выдаёт устройству случайный секрет, в базе - только его хеш.
     * Устройство ничего о себе не сообщает: модель и ANDROID_ID больше не ключ к аккаунту, и чужой по ним не войти.
     * Отвечает аккаунтом и секретом - второй раз его взять неоткуда.
     */
    suspend fun createByDevice(): Pair<User, String> {
        val secret = Tokens.issue()
        val user = User().apply { device_id = Tokens.hash(secret) }
        return transactionExecute("Create by device") { session -> users.insert(user, session, false) } to secret
    }

    /** Вход по секрету устройства; неизвестный секрет - `US_015` без эха самого секрета. */
    suspend fun loginByDevice(secret: String): User {
        val method = "findByDeviceId"
        if (secret.isBlank() || secret.length > Tokens.MAX_LENGTH) throw UserExceptions.funExceptionEmptyDevice(method)
        val user = users.findByDeviceHash(Tokens.hash(secret))?.takeIf { it.isActive }
            ?: throw UserExceptions.funExceptionDeviceNotFound(method)
        user.lastLoginDate = LocalDateTime.now()
        transactionExecute("Device login date") { session -> users.update(user, session) }
        return user
    }

    suspend fun authenticate(login: String, password: String): User {
        val found = users.findByLogin(login)
        // Неизвестный логин проверяется против подставного хеша (1.46.0): по времени ответа не узнать, какие логины заняты.
        val user = found?.takeIf { offCpu { Passwords.verify(password, it.password) } }
            ?: run {
                if (found == null) offCpu { Passwords.verify(password, Passwords.DECOY) }
                null
            }
            ?: throw UserExceptions.funExceptionPasswordLoginPass("authenticate")

        if (!user.isActive) throw UserExceptions.funExceptionInactive("authenticate", user.login)

        // Хеш со слабым числом итераций переписывается сразу, пока пароль в руках: другого случая
        // пересчитать его не будет, а сбрасывать пароли всем игрокам незачем.
        if (Passwords.needsRehash(user.password)) {
            users.storeHash(user._id, offCpu { Passwords.hash(password) })
            // Запись хеша подняла версию в базе - объект в памяти идёт следом, иначе
            // следующая запись споткнулась бы о собственную гонку
            user.version += 1
        }

        return transactionExecute("User authenticate") { session ->
            users.updateFields(user, mapOf("lastLoginDate" to LocalDateTime.now()), session)
        }
    }

    suspend fun changePassword(id: String, password: String, newPassword: String): String {
        val user = users.findById(id)
            ?: throw UserExceptions.funExceptionFoundUserId("changePassword", id)

        if (!offCpu { Passwords.verify(password, user.password) }) {
            throw UserExceptions.funExceptionPasswordLoginPass("changePassword", user.login)
        }

        Passwords.check(newPassword)
        users.storeHash(user._id, offCpu { Passwords.hash(newPassword) })
        return "system.success"
    }

    // ==================== Тестировщики (1.69.0) ====================

    suspend fun testers(): List<TesterAccount> = users.testers().map { it.toTester() }

    /** Новый тестировщик с логином [login] и случайным паролем: пароль в ответе один раз, в базе - только хеш. */
    suspend fun createTester(login: String): TesterAccount {
        val clean = login.trim()
        val password = Passwords.generate()
        val user = transactionExecute("createTester $clean") { session ->
            users.insert(User(name = clean, email = "$clean@$TESTER_MAIL", age = TESTER_AGE, login = clean, password = password, role = EnumUserRoles.TESTER), session)
        }
        return user.toTester(password)
    }

    /** Новый случайный пароль тестировщику: старый перестаёт подходить сразу. */
    suspend fun resetTester(id: String): TesterAccount {
        val user = requireTester(id, "resetTester")
        val password = Passwords.generate()
        users.storeHash(user._id, offCpu { Passwords.hash(password) })
        return user.toTester(password)
    }

    suspend fun setTesterActive(id: String, active: Boolean): TesterAccount {
        val user = requireTester(id, "setTesterActive")
        user.isActive = active
        transactionExecute("setTesterActive $id") { session -> users.update(user, session) }
        return user.toTester()
    }

    private suspend fun requireTester(id: String, method: String): User = users.findById(id)?.takeIf { it.role == EnumUserRoles.TESTER } ?: throw UserExceptions.funExceptionFoundUserId(method, id)

    private fun User.toTester(password: String? = null) = TesterAccount(_id, login, isActive, lastLoginDate?.toString(), password)

    private companion object {
        const val TESTER_MAIL = "tester.exileforge"
        const val TESTER_AGE = 18
    }
}
