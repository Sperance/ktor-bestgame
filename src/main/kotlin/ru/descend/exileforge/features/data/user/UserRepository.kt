package ru.descend.exileforge.features.data.user

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import ru.descend.exileforge.CONST_FIELD_ID
import ru.descend.exileforge.CONST_FIELD_VERSION
import ru.descend.exileforge.application.enums.EnumUserRoles
import ru.descend.exileforge.base.exception.model.UserExceptions
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.features.logic.auth.Passwords

/**
 * Документ аккаунта: уникальность почты, логина и устройства, пароль только хешем. Вход, смена
 * пароля и тестировщики - в [ru.descend.exileforge.features.logic.auth.UserService].
 */
class UserRepository : BaseRepository<User>(User::class) {
    /** Длина логина (1.69.0): то же, что `rules.inputs.login` у клиента. */
    private companion object {
        const val MAX_LOGIN = 32
    }

    // Вход по устройству ищет аккаунт по device_id на каждом старте клиента
    // Аккаунт по устройству живёт без почты и логина: уникальны только заполненные значения,
    // иначе второй такой аккаунт упирался в пустую строку первого
    override val indexes = listOf(
        IndexSpec.uniqueFilled("idx_unique_email_filled", "email"),
        IndexSpec.uniqueFilled("idx_unique_login_filled", "login"),
        IndexSpec.uniqueFilled("idx_unique_device_filled", "device_id"),
    )

    override suspend fun validateBeforeInsert(entity: User, session: ClientSession) {
        if (!entity.email.contains("@")) throw UserExceptions.funExceptionInvalidEmail("validateBeforeInsert", entity.email)
        if (entity.age !in 12..120) throw UserExceptions.funExceptionInvalidAge("validateBeforeInsert", entity.age.toString())
        if (entity.password.length < 6) throw UserExceptions.funExceptionInvalidPassword("validateBeforeInsert")
        if (entity.login.length !in 1..MAX_LOGIN) throw UserExceptions.funExceptionLoginLength("validateBeforeInsert", "1-$MAX_LOGIN")
        // Уникальность проверяется и по мягко удалённым: их документы никуда
        // не делись, и уникальный индекс всё равно не даст занять логин или почту
        if (findByLogin(entity.login, includeDeleted = true) != null) throw UserExceptions.funExceptionLoginExists("validateBeforeInsert", entity.login)
        if (findByEmail(entity.email, includeDeleted = true) != null) throw UserExceptions.funExceptionEmailExists("validateBeforeInsert", entity.email)

        Passwords.check(entity.password)
        // Соль и число итераций живут внутри строки хеша.
        entity.password = Passwords.offCpu { Passwords.hash(entity.password) }
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

        // Пароль и идентификатор устройства - это ключи от аккаунта. Общий PUT записал
        // бы пароль как есть, без хеша, а device_id - это вход без пароля; меняются они только
        // своими маршрутами.
        listOf("password", "device_id").forEach { field ->
            if (changes.containsKey(field)) throw UserExceptions.funExceptionSalt("validateBeforeUpdate", field)
        }
    }

    /**
     * @param includeDeleted true - найдётся и мягко удалённый пользователь
     */
    suspend fun findByEmail(email: String, includeDeleted: Boolean = false): User? = findByField(User::email, email, includeDeleted)

    /**
     * @param includeDeleted true - найдётся и мягко удалённый пользователь
     */
    suspend fun findByLogin(login: String, includeDeleted: Boolean = false): User? = findByField(User::login, login, includeDeleted)

    /** Аккаунт по хешу секрета устройства. */
    suspend fun findByDeviceHash(hash: String): User? = findByField(User::device_id, hash)

    /**
     * Отмечает промокод [codeId] за аккаунтом одной условной записью: из двух параллельных
     * активаций (хоть разными персонажами) проходит одна. Версия растёт, чтобы полная запись
     * по устаревшему чтению не стёрла отметку. Отвечает, была ли отметка новой.
     */
    suspend fun claimRedemption(userId: String, codeId: String, session: ClientSession): Boolean = collection.updateOne(
        session,
        Filters.and(Filters.eq(CONST_FIELD_ID, userId), Filters.ne(User::redeemedCodes.name, codeId)),
        Updates.combine(Updates.addToSet(User::redeemedCodes.name, codeId), Updates.inc(CONST_FIELD_VERSION, 1L)),
    ).modifiedCount == 1L

    /**
     * Записывает хеш пароля в обход общего обновления: оно запрещает менять пароль,
     * потому что через него пароль лёг бы в базу открытым текстом. Версия всё равно растёт -
     * чужая запись, начатая раньше, не перетрёт новый хеш.
     */
    suspend fun storeHash(userId: String, hash: String) {
        collection.updateOne(
            Filters.eq("_id", userId),
            Updates.combine(Updates.set("password", hash), Updates.inc("version", 1L)),
        )
    }

    suspend fun testers(): List<User> = findByFilter(Filters.eq("role", EnumUserRoles.TESTER.name)).sortedBy { it.login }
}

/** Аккаунт тестировщика для администратора (1.69.0); [password] - только в ответе на создание и сброс. */
@kotlinx.serialization.Serializable
data class TesterAccount(val id: String, val login: String, val active: Boolean, val lastLogin: String?, val password: String? = null)
