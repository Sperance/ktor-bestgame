package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Зароленный модификатор конкретного экземпляра предмета.
 *
 * Хранится внутри документа инвентаря персонажа и ссылается
 * на [ModifierDefinition] и на выпавший [ModifierTier].
 */
@Serializable
data class Modifier(

    /**
     * Ссылка на [ModifierDefinition._id].
     */
    val modifierId: String,

    /**
     * Ссылка на [ModifierTier._id].
     */
    val tierId: String,

    /**
     * Номер выпавшего тира. Дублирует [ModifierTier.tier] для чтения без join.
     */
    val tier: Int,

    /**
     * Значение, выпавшее внутри диапазона тира.
     */
    val value: Double
)
