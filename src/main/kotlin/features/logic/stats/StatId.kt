package features.logic.stats

import kotlinx.serialization.Serializable

/**
 * Уникальный идентификатор характеристики.
 *
 * В отличие от enum позволяет добавлять новые статы
 * без изменения серверного кода.
 *
 * Примеры:
 *
 * life
 * mana
 * strength
 * armor
 * fire_resistance
 * physical_damage
 * spell_damage
 */
@JvmInline
@Serializable
value class StatId(
    val value: String
) {
    init {
        require(value.isNotBlank()) {
            "StatId cannot be blank"
        }
    }

    override fun toString(): String = value
}