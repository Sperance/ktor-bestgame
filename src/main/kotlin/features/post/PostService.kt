package features.post

import base.exception.NotFoundException
import base.service.BaseService
import features.user.UserRepository

class PostService(
    private val postRepo: PostRepository = PostRepository(),
    private val userRepo: UserRepository = UserRepository()
) : BaseService<PostResponse, CreatePostRequest, UpdatePostRequest, PostsTable>(postRepo) {

    override fun entityName() = "Post"

    override fun validateCreate(request: CreatePostRequest) {
        require(request.title.isNotBlank()) { "title must not be blank" }
        require(request.title.length <= 500) { "title must be ≤ 500 chars" }
        require(request.content.isNotBlank()) { "content must not be blank" }
        if (!userRepo.exists(request.authorId)) {
            throw NotFoundException("Author(id=${request.authorId}) not found")
        }
    }

    override fun validateUpdate(id: Long, request: UpdatePostRequest) {
        request.title?.let {
            require(it.isNotBlank()) { "title must not be blank" }
            require(it.length <= 500) { "title must be ≤ 500 chars" }
        }
        request.content?.let { require(it.isNotBlank()) { "content must not be blank" } }
    }

    fun findByAuthor(authorId: Long) = postRepo.findByAuthor(authorId)
    fun findPublished() = postRepo.findPublished()
    fun searchByTitle(keyword: String) = postRepo.searchByTitle(keyword)
    fun findPublishedWithAuthors() = postRepo.findPublishedWithAuthors()
    fun countByAuthor(authorId: Long) = postRepo.countByAuthor(authorId)
}
