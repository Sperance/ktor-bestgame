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
     * Выпавшие значения, по одному на каждый эффект описания и в том же порядке.
     * У обычного модификатора значение одно, у составного - несколько.
     */
    val values: List<Double>
)
