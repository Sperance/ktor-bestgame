package ru.descend.features.character.model

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterItems")
data class CharacterItems(
    var itemId: String,
    var amount: Long
)
