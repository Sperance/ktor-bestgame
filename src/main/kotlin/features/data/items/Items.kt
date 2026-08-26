package features.data.items

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(
    val type: ItemType,
    val description: String? = null,
    val image: String? = null,
    val price: Long = 0,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
data class ItemType(
    val category: String? = null,
    val subCategory: String? = null,
    val name: String? = null,
) {
    fun getFirstCorrect(): String? {
        return name ?: subCategory ?: category
    }
}