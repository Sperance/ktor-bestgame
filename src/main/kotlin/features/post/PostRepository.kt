package features.post

import base.repository.BaseRepository
import features.user.UsersTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostRepository : BaseRepository<PostResponse, CreatePostRequest, UpdatePostRequest, PostsTable>(
    table = PostsTable,
    entityClass = PostResponse::class
) {
    override val entityName = "Post"

    fun findByAuthor(authorId: Long): List<PostResponse> = transaction {
        table.selectAll()
            .where { table.authorId eq authorId }
            .orderBy(table.createdAt, SortOrder.DESC)
            .map(::toEntity)
    }

    fun findPublished(): List<PostResponse> = transaction {
        table.selectAll()
            .where { table.isPublished eq true }
            .orderBy(table.createdAt, SortOrder.DESC)
            .map(::toEntity)
    }

    fun searchByTitle(keyword: String): List<PostResponse> = transaction {
        table.selectAll()
            .where { table.title like "%$keyword%" }
            .map(::toEntity)
    }

    /**
     * JOIN — маппинг вручную, т.к. две таблицы.
     * Рефлексия работает для одной таблицы; JOIN'ы — нестандартные запросы.
     */
    fun findPublishedWithAuthors(): List<PostWithAuthorResponse> = transaction {
        (PostsTable innerJoin UsersTable)
            .selectAll()
            .where { PostsTable.isPublished eq true }
            .orderBy(PostsTable.createdAt, SortOrder.DESC)
            .map { row ->
                PostWithAuthorResponse(
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

    fun countByAuthor(authorId: Long): Long = transaction {
        table.selectAll().where { table.authorId eq authorId }.count()
    }
}
