package features.data.character.character_data

import application.enums.EnumStatProfession
import kotlinx.serialization.Serializable

@Serializable
data class CharacterProfessionSkill(
    val stat: EnumStatProfession,
    val level: Byte,
    val experience: Double = 0.0
)