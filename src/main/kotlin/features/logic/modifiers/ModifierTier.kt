package features.logic.modifiers

import base.entity.StockEntity
import extensions.RandomExt
import extensions.to1Digits
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Тир модификатора. Отдельная коллекция Mongo `ModifierTier`.
 *
 * Один документ = один тир одного [ModifierDefinition]:
 *
 * "Fire Resistance Tier 1" → 46.0..48.0, доступен с itemLevel 84.
 *
 * Нумерация как в POE: тир 1 - лучший, он даёт максимальные значения
 * и требует самый высокий item level, дальше тиры слабеют.
 */
@Serializable
data class ModifierTier(

    /**
     * Ссылка на [ModifierDefinition._id].
     */
    val modifierId: String,

    /**
     * Номер тира, начиная с 1. Тир 1 - лучший.
     */
    val tier: Int,

    /**
     * Нижняя граница значения (включительно).
     */
    val valueMin: Double,

    /**
     * Верхняя граница значения (включительно).
     */
    val valueMax: Double,

    /**
     * Минимальный item level предмета, на котором тир может выпасть.
     */
    val minItemLevel: Int = 1,

    /**
     * Вес тира при взвешенном ролле среди доступных тиров.
     * Чем лучше тир, тем меньше вес.
     */
    val weight: Int = 1,

    override var _id: String = ObjectId().toHexString()
) : StockEntity {

    /**
     * Роллит конкретное значение внутри диапазона тира.
     */
    fun rollValue(): Double =
        if (valueMin >= valueMax) valueMax.to1Digits()
        else RandomExt.randomDouble(valueMin, valueMax).to1Digits()
}
