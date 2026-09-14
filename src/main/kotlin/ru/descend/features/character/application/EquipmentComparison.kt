package ru.descend.features.character.application

import kotlinx.serialization.Serializable
import ru.descend.features.character.domain.CharacterStats

@Serializable data class EquipmentComparison(val characterVersion: Long, val allowed: Boolean, val reason: String? = null, val before: CharacterStats, val after: CharacterStats? = null)
@Serializable data class CraftOption(val currency: String, val name: String, val itemId: String, val amount: Long, val available: Boolean, val reason: String? = null)
@Serializable data class CraftOptions(val characterVersion: Long, val options: List<CraftOption>)
