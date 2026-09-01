package features.data.character.character_data

import application.enums.EnumStatStock
import kotlinx.serialization.Serializable

@Serializable
data class CharacterStockSkill(
    val stat: EnumStatStock,
    val value: Int,
)