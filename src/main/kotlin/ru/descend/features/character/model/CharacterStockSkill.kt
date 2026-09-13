package ru.descend.features.character.model

import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumStatStock

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterStockSkill")
data class CharacterStockSkill(
    val stat: EnumStatStock,
    val value: Int,
)
