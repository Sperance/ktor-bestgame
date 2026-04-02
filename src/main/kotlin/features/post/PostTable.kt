package features.post

import base.annotations.DefaultValue
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.model.BaseEntity
import base.table.BaseTable
import features.user.UsersTable
import kotlinx.serialization.Serializable

object PostsTable : BaseTable("posts") {
    val title = varchar("title", 500)
    val content = text("content")
    val authorId = long("author_id").references(UsersTable.id)
    val isPublished = bool("is_published").default(false)
}

@Serializable
data class Post(
    @ReadOnly
    override val id: Long? = null,

    val title: String,

    val content: String,

    @Immutable  // нельзя менять автора после создания
    val authorId: Long,

    @DefaultValue("false")
    val isPublished: Boolean = false,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity