package features.property

import base.annotations.ReadOnly
import base.annotations.Required
import base.model.BaseEntity
import base.table.BaseTable
import kotlinx.serialization.Serializable

object PropertyTable : BaseTable("property") {
    val code = varchar(name = "code", length = 32).uniqueIndex()
    val name = varchar(name = "name", length = 64).uniqueIndex()
    val image = text("image").nullable()
}

@Serializable
data class Property(
    @ReadOnly
    override val id: Long = -1,

    @Required
    val code: String = "",

    @Required
    val name: String = "",

    val image: String? = null,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity
