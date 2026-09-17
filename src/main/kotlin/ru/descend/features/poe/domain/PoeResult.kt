package ru.descend.features.poe.domain

import kotlinx.serialization.Serializable
import ru.descend.features.character.model.CharacterEquipments

@Serializable
@kotlinx.serialization.SerialName("features.poe.PoeResult")
data class PoeResult(val requestId: String, val characterVersion: Long, val equipment: CharacterEquipments, val currencyRemaining: Long? = null)
