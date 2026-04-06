package features.post

import base.exception.NotFoundException
import base.service.BaseService
import features.user.UserRepository

class PostService(
    private val postRepo: PostRepository = PostRepository(),
    private val userRepo: UserRepository = UserRepository()
) : BaseService<Post, PostsTable>(postRepo, Post.serializer()) {

    override fun entityName() = "Post"

    override fun validateCreate(entity: Post) {
        if (!userRepo.exists(entity.authorId)) {
            throw NotFoundException("Author(id=${entity.authorId}) not found")
        }
    }

    fun findByAuthor(authorId: Long) = postRepo.findByAuthor(authorId)
    fun findPublished() = postRepo.findPublished()
}