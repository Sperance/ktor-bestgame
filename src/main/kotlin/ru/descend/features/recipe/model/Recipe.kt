package ru.descend.features.recipe.model

import ru.descend.domain.enums.IntEnumStat
import ru.descend.shared.model.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
@kotlinx.serialization.SerialName("features.data.recipe.Recipe")
data class Recipe(
    val name: String,
    val arrayIn: List<RecipeParamIn> = listOf(),
    val arrayOut: List<RecipeParamOut> = listOf(),
    val requirement: List<RecipeRequirement>? = null,
    val timeWork: Double = 1.0,
    val needOpenRecipe: Boolean = false,
    var globalUses: Long = 0L,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
@kotlinx.serialization.SerialName("features.data.recipe.RecipeParamIn")
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
@kotlinx.serialization.SerialName("features.data.recipe.RecipeParamOut")
data class RecipeParamOut(
    val itemId: String,
    val amount: Double = 1.0,
    val chance: Double = 1.0
)

@Serializable
@kotlinx.serialization.SerialName("features.data.recipe.RecipeRequirement")
data class RecipeRequirement(
    val stat: IntEnumStat,
    val value: Int
)

@Serializable
@kotlinx.serialization.SerialName("features.data.recipe.RecipeUse")
data class RecipeUse(
    val ingridientsId: List<String>,
    val amount: Long
)
