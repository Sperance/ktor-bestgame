package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Теги modifier.
 *
 * Нужны для:
 *
 * crafting
 * filtering
 * conditional modifiers
 * item generation
 * balance
 * compatibility
 */
@Serializable
@JvmInline
value class ModifierTag(
    val value: String
) {
    init {
        require(value.isNotBlank()) {
            "ModifierTag cannot be blank"
        }
    }

    override fun toString(): String = value
}