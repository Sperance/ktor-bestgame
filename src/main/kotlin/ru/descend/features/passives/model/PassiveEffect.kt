package ru.descend.features.passives.model

import kotlinx.serialization.Serializable

@Serializable enum class PassiveOperation { FLAT, INCREASED, REDUCED, MORE, LESS }
@Serializable data class PassiveEffect(val stat: String, val operation: PassiveOperation, val value: Double)
