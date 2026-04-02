package features.post

import base.model.BaseEntity
import base.model.CreateRequest
import base.model.UpdateRequest
import kotlinx.serialization.Serializable

@Serializable
data class PostResponse(
    override val id: Long? = null,
    val title: String,
    val content: String,
    val authorId: Long,
    val isPublished: Boolean = false,
    override val version: Long = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
) : BaseEntity

@Serializable
data class PostWithAuthorResponse(
    val id: Long,
    val title: String,
    val content: String,
    val authorId: Long,
    val authorName: String,
    val authorEmail: String,
    val isPublished: Boolean,
    val version: Long,
    val createdAt: String? = null
)

@Serializable
data class CreatePostRequest(
    val title: String,
    val content: String,
    val authorId: Long,
    val isPublished: Boolean = false
) : CreateRequest

@Serializable
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val isPublished: Boolean? = null,
    override val version: Long
) : UpdateRequest
