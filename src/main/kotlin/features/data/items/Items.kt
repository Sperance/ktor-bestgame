package features.data.items

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(
    val name: String,
    val category: ItemType,
    val description: String? = null,
    val image: String? = null,
    val price: Long = 0,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
data class ItemType(
    val category: String? = null,
    val subCategory: String? = null,
)