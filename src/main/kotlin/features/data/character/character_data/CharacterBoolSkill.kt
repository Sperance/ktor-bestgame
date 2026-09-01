package features.data.character.character_data

import application.enums.EnumStatBool
import kotlinx.serialization.Serializable

@Serializable
data class CharacterBoolSkill(
    val stat: EnumStatBool,
    var value: Boolean? = null
)