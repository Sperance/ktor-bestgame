package ru.descend.domain.modifiers

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.logic.modifiers.ModifierRef")
data class ModifierRef(val definitionId: String, val revision: Int = 1) {
    init { require(definitionId.isNotBlank() && revision > 0) }
}
