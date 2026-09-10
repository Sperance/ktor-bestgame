package features.logic.modifiers

import kotlinx.serialization.Serializable

@Serializable
data class ModifierRef(val definitionId: String, val revision: Int = 1) {
    init { require(definitionId.isNotBlank() && revision > 0) }
}
