package features.user

import base.exception.ConflictException
import base.service.BaseService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class UserService(
    private val repo: UserRepository = UserRepository()
) : BaseService<User, UsersTable>(repo) {

    override fun entityName() = "User"

    override fun validateCreate(json: JsonObject) {
        val email = json["email"]?.jsonPrimitive?.content
        email?.let {
            repo.findByEmail(it)?.let {
                throw ConflictException("Email '$email' is already taken")
            }
        }
    }

    override fun validateUpdate(id: Long, json: JsonObject) {
        val email = json["email"]?.jsonPrimitive?.content
        email?.let {
            repo.findByEmail(it)?.let { existing ->
                if (existing.id != id) throw ConflictException("Email '$email' is already taken")
            }
        }
    }

    fun findByEmail(email: String) = repo.findByEmail(email)
    fun searchByName(name: String) = repo.searchByName(name)
    fun findActive() = repo.findActive()
}