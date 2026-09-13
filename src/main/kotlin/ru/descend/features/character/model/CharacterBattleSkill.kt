package ru.descend.features.character.model

import ru.descend.domain.enums.EnumStatBattle
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterBattleSkill")
data class CharacterBattleSkill(
    val stat: EnumStatBattle,
    val level: Byte,
    val experience: Double = 0.0
)
