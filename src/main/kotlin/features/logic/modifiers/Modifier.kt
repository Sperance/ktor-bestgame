package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Применённый модификатор: зароленный на экземпляре предмета
 * или закреплённый за узлом дерева навыков.
 *
 * С 0.56.0 ссылается на [ModifierDefinition] по стабильному коду, а тир - просто номер
 * внутри [ModifierDefinition.tiers].
 */
@Serializable
data class Modifier(

    /**
     * Ссылка на [ModifierDefinition.code].
     */
    val modifierCode: String,

    /**
     * Выпавшие значения, по одному на каждый эффект описания и в том же порядке.
     * У обычного модификатора значение одно, у составного - несколько.
     */
    val values: List<Double>,

    /**
     * Номер выпавшего тира, 1 - лучший. Ноль у пассивок дерева и базы шаблона: тиров у них нет.
     */
    val tier: Int = 0,

    /**
     * Закреплённый (fractured) аффикс: его поставила Fracturing Orb, и дальше его
     * не снимает, не перекатывает и не меняет ни одна сфера.
     */
    val fractured: Boolean = false,
) {

    companion object {
        /**
         * Пассивный модификатор дерева навыков или базы шаблона: значения фиксированы,
         * ролла и тира у него нет.
         */
        fun passive(modifierCode: String, values: List<Double>) = Modifier(modifierCode, values)
    }
}
