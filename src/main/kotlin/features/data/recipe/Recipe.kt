package features.data.recipe

import application.enums.EnumCharacterSkills
import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Recipe(
    val name: String,
    val arrayIn: List<RecipeParamIn> = listOf(),
    val arrayOut: List<RecipeParamOut> = listOf(),
    val requirement: List<RecipeRequirement>? = null,
    val timeWork: Long = 1L,
    val needOpenRecipe: Boolean = false,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
data class RecipeParamIn(
    val category: String? = null,
    val subCategory: String? = null,
    val itemId: String? = null,
    val amount: Double = 1.0,
) {
    fun countCorrect(): Int {
        var result = 0
        if (itemId != null) result += 1
        if (subCategory != null) result += 1
        if (category != null) result += 1
        return result
    }
}

@Serializable
data class RecipeParamOut(
    val itemId: String,
    val amount: Double = 1.0,
    val chance: Double = 1.0
)

@Serializable
data class RecipeRequirement(
    val stat: EnumCharacterSkills,
    val level: Byte
)

@Serializable
data class RecipeUse(
    val ingridientsId: List<String>,
    val amount: Long
)