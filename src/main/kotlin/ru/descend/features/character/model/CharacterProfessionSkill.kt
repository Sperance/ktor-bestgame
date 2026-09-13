package ru.descend.features.character.model

import ru.descend.domain.enums.EnumStatProfession
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterProfessionSkill")
data class CharacterProfessionSkill(
    val stat: EnumStatProfession,
    val level: Byte,
    val experience: Double = 0.0
)
