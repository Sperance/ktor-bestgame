package features.post

import base.repository.BaseRepository
import features.user.UsersTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
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

    fun findPublishedWithAuthors(): List<PostWithAuthor> = transaction {
        (PostsTable innerJoin UsersTable)
            .selectAll()
            .where { PostsTable.isPublished eq true }
            .map { row ->
                PostWithAuthor(
                    id = row[PostsTable.id],
                    title = row[PostsTable.title],
                    content = row[PostsTable.content],
                    authorId = row[PostsTable.authorId],
                    authorName = row[UsersTable.name],
                    authorEmail = row[UsersTable.email],
                    isPublished = row[PostsTable.isPublished],
                    version = row[PostsTable.version],
                    createdAt = row[PostsTable.createdAt].toString()
                )
            }
    }
}