package ru.descend.features.character.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.GainedRedemtionCodes")
data class GainedRedemtionCodes(
    val redemptionCodeId: String,
    val dateGained: LocalDateTime
)
