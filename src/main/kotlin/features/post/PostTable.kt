package features.post

import base.table.BaseTable
import features.user.UsersTable

object PostsTable : BaseTable("posts") {
    val title = varchar("title", 500)
    val content = text("content")
    val authorId = long("author_id").references(UsersTable.id)
    val isPublished = bool("is_published").default(false)
}