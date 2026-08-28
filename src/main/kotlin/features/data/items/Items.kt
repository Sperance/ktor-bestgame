package features.data.items

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(
    val name: String,
    val category: String,
    val subCategory: String,
    val description: String = "",
    val image: String? = null,
    val price: Long = 0,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity