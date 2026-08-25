package features.data.items

import base.entity.StockEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(
    val name: String = "",
    val description: String? = null,
    val image: String? = null,
    val price: Long = 0,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity