package features.user

import base.exception.ConflictException
import base.service.BaseService

class UserService(
    private val repo: UserRepository = UserRepository()
) : BaseService<UserResponse, CreateUserRequest, UpdateUserRequest, UsersTable>(repo) {

    override fun entityName() = "User"

    override fun validateCreate(request: CreateUserRequest) {
        require(request.name.isNotBlank()) { "name must not be blank" }
        require(request.email.isNotBlank()) { "email must not be blank" }
        require("@" in request.email) { "email format is invalid" }
        request.age?.let { require(it in 0..150) { "age must be 0..150" } }

        repo.findByEmail(request.email)?.let {
            throw ConflictException("Email '${request.email}' is already taken")
        }
    }

    override fun validateUpdate(id: Long, request: UpdateUserRequest) {
        request.name?.let { require(it.isNotBlank()) { "name must not be blank" } }
        request.email?.let { email ->
            require("@" in email) { "email format is invalid" }
            repo.findByEmail(email)?.let { existing ->
                if (existing.id != id) throw ConflictException("Email '$email' is already taken")
            }
        }
        request.age?.let { require(it in 0..150) { "age must be 0..150" } }
    }

    fun findByEmail(email: String) = repo.findByEmail(email)
    fun searchByName(name: String) = repo.searchByName(name)
    fun findActive() = repo.findActive()
}
