package features.post

import base.exception.NotFoundException
import base.service.BaseService
import features.user.UserRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class PostService(
    private val postRepo: PostRepository = PostRepository(),
    private val userRepo: UserRepository = UserRepository()
) : BaseService<Post, PostsTable>(postRepo) {

    override fun entityName() = "Post"

    override fun validateCreate(json: JsonObject) {
        val authorId = json["authorId"]?.jsonPrimitive?.long
        authorId?.let {
            if (!userRepo.exists(it)) throw NotFoundException("Author(id=$it) not found")
        }
    }

    fun findByAuthor(authorId: Long) = postRepo.findByAuthor(authorId)
    fun findPublished() = postRepo.findPublished()
}