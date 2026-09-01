package features.data.character.character_data

import kotlinx.serialization.Serializable

@Serializable
data class CharacterItems(
    var itemId: String,
    var amount: Long
)