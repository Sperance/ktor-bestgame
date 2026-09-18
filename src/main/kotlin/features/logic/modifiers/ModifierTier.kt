package features.logic.modifiers

import base.entity.StockEntity
import extensions.RandomExt
import extensions.to1Digits
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Диапазон значений одного эффекта модификатора внутри тира.
 */
@Serializable
data class ModifierTierValue(

    /**
     * Нижняя граница значения (включительно).
     */
    val valueMin: Double,

    /**
     * Верхняя граница значения (включительно).
     */
    val valueMax: Double,
)

/**
 * Тир модификатора. Отдельная коллекция Mongo `ModifierTier`.
 *
 * Один документ = один тир одного [ModifierDefinition]:
 *
 * "Fire Resistance Tier 1" → 46.0..48.0, доступен с itemLevel 84.
 *
 * Нумерация как в POE: тир 1 - лучший, он даёт максимальные значения
 * и требует самый высокий item level, дальше тиры слабеют.
 *
 * У составного модификатора в [values] лежит по диапазону на каждый
 * эффект описания, в том же порядке.
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
     * Диапазоны значений, по одному на каждый эффект описания.
     */
    val values: List<ModifierTierValue>,

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
     * Роллит значения тира - по одному на каждый эффект описания.
     *
     * Качество ролла общее для всех эффектов: составной модификатор
     * не может выпасть максимумом по здоровью и минимумом по мане.
     */
    fun roll(): List<Double> {
        val progress = RandomExt.randomProgress()
        return values.map { it.valueMin + (it.valueMax - it.valueMin) * progress }
            .map { it.to1Digits() }
    }
}
