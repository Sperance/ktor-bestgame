package features.post

import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostRepository : BaseRepository<Post, PostsTable>(
    table = PostsTable,
    entityClass = Post::class
) {
    override val entityName = "Post"

    fun findByAuthor(authorId: Long): List<Post> = transaction {
        table.selectAll()
            .where { table.authorId eq authorId }
            .orderBy(table.createdAt, SortOrder.DESC)
            .map(::toEntity)
    }

    fun findPublished(): List<Post> = transaction {
        table.selectAll()
            .where { table.isPublished eq true }
            .orderBy(table.createdAt, SortOrder.DESC)
            .map(::toEntity)
    }
}