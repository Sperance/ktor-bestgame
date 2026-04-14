package features.user

import base.exception.BadRequestException
import base.exception.ConflictException
import base.exception.UnauthorizedException
import base.service.BaseService
import java.security.MessageDigest
import java.security.SecureRandom

class UserService(
    private val repo: UserRepository = UserRepository()
) : BaseService<User, UsersTable>(repo, User.serializer()) {

    override fun entityName() = "User"

    // ==================== Validation ====================

    override fun validateCreate(entity: User) {
        if (entity.password.length < 6) {
            throw BadRequestException("Password must be at least 6 characters")
        }
        if (entity.salt != "") {
            throw BadRequestException("Field 'salt' has blocked to modify")
        }
    }

    override fun validateUpdate(id: Long, entity: User) {
        repo.findByEmail(entity.email)?.let { existing ->
            if (existing.id != id) throw ConflictException("Email '${entity.email}' is already taken")
        }
    }

    // ==================== Transform ====================

    override fun transformCreate(entity: User): User {
        val salt = generateSalt()
        return entity.copy(
            password = hashPassword(entity.password, salt),
            salt = salt
        )
    }

    override fun transformUpdate(id: Long, entity: User): User {
        if (entity.password.isEmpty()) return entity  // пароль не меняется
        val salt = generateSalt()
        return entity.copy(
            password = hashPassword(entity.password, salt),
            salt = salt
        )
    }

    // ==================== Queries ====================

    fun findByEmail(email: String) = repo.findByEmail(email)
    fun findByLogin(login: String) = repo.findByLogin(login)
    fun searchByName(name: String) = repo.searchByName(name)
    fun findActive() = repo.findActive()

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
    fun authenticate(login: String, password: String): User {
        val credentials = repo.findCredentialsByLogin(login)
            ?: throw UnauthorizedException("Invalid login or password")

        val (userId, storedHash, storedSalt) = credentials

        if (hashPassword(password, storedSalt) != storedHash) {
            throw UnauthorizedException("Invalid login or password")
        }

        val user = getById(userId)

        if (!user.isActive) {
            throw UnauthorizedException("Account is deactivated")
        }

        return user
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