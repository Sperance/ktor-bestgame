package ru.descend.features.character.model

import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumStatProfession

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterProfessionSkill")
data class CharacterProfessionSkill(
    val stat: EnumStatProfession,
    val level: Byte,
    val experience: Double = 0.0
)
