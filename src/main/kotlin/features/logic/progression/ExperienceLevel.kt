package features.logic.progression

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Ступень прогрессии. Отдельная коллекция Mongo `ExperienceLevel`.
 *
 * Одна таблица отвечает и за то, когда персонаж поднимается на уровень,
 * и за то, сколько очков дерева навыков он за это получает.
 */
@Serializable
data class ExperienceLevel(

    /**
     * Номер уровня, начиная с первого.
     */
    val level: Int,

    /**
     * Опыт, с которого уровень считается достигнутым.
     * У первого уровня ноль.
     */
    val experience: Double,

    /**
     * Сколько очков дерева навыков даёт достижение этого уровня.
     */
    val skillPoints: Int = 1,

    override var _id: String = ObjectId().toHexString()
) : StockEntity
