package ru.descend.features.character.model

import ru.descend.domain.enums.EnumStatBool
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterBoolSkill")
data class CharacterBoolSkill(
    val stat: EnumStatBool,
    var value: Boolean? = null
)
