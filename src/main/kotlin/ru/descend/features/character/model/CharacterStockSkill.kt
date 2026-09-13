package ru.descend.features.character.model

import ru.descend.domain.enums.EnumStatStock
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterStockSkill")
data class CharacterStockSkill(
    val stat: EnumStatStock,
    val value: Int,
)
