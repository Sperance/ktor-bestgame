package features.data.character.character_data

import application.enums.EnumStatBattle
import kotlinx.serialization.Serializable

@Serializable
data class CharacterBattleSkill(
    val stat: EnumStatBattle,
    val level: Byte,
    val experience: Double = 0.0
)