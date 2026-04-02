package features.user

import base.model.BaseEntity
import base.model.CreateRequest
import base.model.UpdateRequest
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    override val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int? = null,
    val isActive: Boolean = true,
    override val version: Long = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : BaseEntity

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val age: Int? = null,
    val isActive: Boolean = true
) : CreateRequest

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null,
    val age: Int? = null,
    val isActive: Boolean? = null,
    override val version: Long
) : UpdateRequest
