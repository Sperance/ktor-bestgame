package features.data.character.character_data

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class GainedRedemtionCodes(
    val redemptionCodeId: String,
    val dateGained: LocalDateTime
)