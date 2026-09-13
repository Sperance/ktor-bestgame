package ru.descend.features.items.model

import ru.descend.shared.model.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
@kotlinx.serialization.SerialName("features.data.items.Items")
data class Items(
    val name: String,
    val category: String,
    val subCategory: String,
    val description: String = "",
    val image: String? = null,
    val price: Long = 0,

    val poeBaseId: String? = null,
    override var _id: String = ObjectId().toHexString(),
) : StockEntity
