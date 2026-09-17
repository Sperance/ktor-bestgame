package ru.descend.features.items.model

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.shared.model.VersionedEntity
import kotlinx.datetime.LocalDateTime
import ru.descend.shared.extensions.now

@Serializable
@kotlinx.serialization.SerialName("features.data.items.Items")
data class Items(
    val name: String,
    val category: String,
    val subCategory: String,
    val description: String = "",
    val image: String? = null,
    /** Идентификатор иконки набора. Картинка: /api/v1/icons/{icon}.svg */
    val icon: String? = null,
    val price: Long = 0,

    val poeBaseId: String? = null,
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity
