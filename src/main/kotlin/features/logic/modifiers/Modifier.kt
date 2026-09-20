package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Применённый модификатор: зароленный на экземпляре предмета
 * или закреплённый за узлом дерева навыков.
 *
 * Ссылается на [ModifierDefinition], а у предметов - ещё и на выпавший [ModifierTier].
 */
@Serializable
data class Modifier(

    /**
     * Ссылка на [ModifierDefinition._id].
     */
    val modifierId: String,

    /**
     * Выпавшие значения, по одному на каждый эффект описания и в том же порядке.
     * У обычного модификатора значение одно, у составного - несколько.
     */
    val values: List<Double>,

    /**
     * Ссылка на [ModifierTier._id]. Пусто у пассивок дерева: тиров у них нет.
     */
    val tierId: String = "",

    /**
     * Номер выпавшего тира. Дублирует [ModifierTier.tier] для чтения без join.
     * Ноль у пассивок дерева.
     */
    val tier: Int = 0,
) {
    /**
     * Модификатор с тиром - то есть зароленный на предмете.
     */
    fun isRolled(): Boolean = tierId.isNotEmpty()

    companion object {
        /**
         * Пассивный модификатор дерева навыков: значения фиксированы узлом,
         * ролла и тира у него нет.
         */
        fun passive(modifierId: String, values: List<Double>) = Modifier(modifierId, values)
    }
}
