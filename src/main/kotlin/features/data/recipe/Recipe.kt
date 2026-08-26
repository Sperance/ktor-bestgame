package features.data.recipe

import base.entity.StockEntity
import features.data.items.ItemType
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Recipe(
    var name: String,
    val arrayIn: MutableList<RecipeParam> = mutableListOf(),
    val arrayOut: MutableList<RecipeParam> = mutableListOf(),
    var timeWork: Long = 1L,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
data class RecipeParam(
    val itemType: ItemType,
    val amount: Double,
)