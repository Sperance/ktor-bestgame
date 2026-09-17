package ru.descend.features.character.model

import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumStatBool

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterBoolSkill")
data class CharacterBoolSkill(
    val stat: EnumStatBool,
    var value: Boolean? = null
)
