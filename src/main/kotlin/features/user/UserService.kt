package features.user

import base.exception.ConflictException
import base.service.BaseService

class UserService(
    private val repo: UserRepository = UserRepository()
) : BaseService<User, UsersTable>(repo, User.serializer()) {

    override fun entityName() = "User"

    override fun validateCreate(entity: User) {
        repo.findByEmail(entity.email)?.let {
            throw ConflictException("Email '${entity.email}' is already taken")
        }
    }

    override fun validateUpdate(id: Long, entity: User) {
        repo.findByEmail(entity.email)?.let { existing ->
            if (existing.id != id) throw ConflictException("Email '${entity.email}' is already taken")
        }
    }

    fun findByEmail(email: String) = repo.findByEmail(email)
    fun searchByName(name: String) = repo.searchByName(name)
    fun findActive() = repo.findActive()
}