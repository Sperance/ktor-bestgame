package features.data.recipe

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import server.serializers.ObjectIdSerializer
import org.bson.types.ObjectId

@Serializable
data class Recipe(
    var name: String,
    val arrayIn: MutableList<RecipeParam> = mutableListOf(),
    val arrayOut: MutableList<RecipeParam> = mutableListOf(),
    var timeWork: Long = 1L,

    @Serializable(with = ObjectIdSerializer::class)
    override var _id: ObjectId = ObjectId(),
) : StockEntity

@Serializable
data class RecipeParam(
    val item: String,
    val amount: Double,
)