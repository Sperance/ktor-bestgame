package features.items

import base.annotations.ReadOnly
import base.annotations.Required
import base.model.BaseEntity
import base.table.BaseTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.insert

object ItemsTable : BaseTable("items") {

    /** Название предмета (видимое игроку) */
    val name = varchar("name", 100).uniqueIndex()

    /** Текстовое описание / лор предмета */
    val description = varchar("description", 500).nullable()

    val image = text("image").nullable()

    val price = ulong("price")
}

@Serializable
data class Item(
    @ReadOnly
    override val id: Long? = null,

    @Required
    val name: String = "",

    val description: String? = null,

    val image: String? = null,

    @Required
    val price: ULong = 0u,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity
