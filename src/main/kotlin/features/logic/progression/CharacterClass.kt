package features.logic.progression

import application.enums.IntEnumStat
import base.entity.StockEntity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Значение стата в справочных данных: база класса, прирост за уровень.
 */
@Serializable
data class StatValue(
    val stat: IntEnumStat,
    val value: Double,
)

/**
 * Класс персонажа. Отдельная коллекция Mongo `CharacterClass`.
 *
 * Задаёт базу, от которой считаются все проценты, и точку входа в дерево навыков.
 *
 * Персонаж ссылается на класс, а не хранит его снимок: база класса - константа
 * мира, а не выбор игрока, поэтому её перебалансировка должна доезжать до всех.
 * Снимок в проекте делается там, где игрок что-то выбрал: ролл предмета,
 * взятый узел дерева.
 */
@Serializable
data class CharacterClass(

    /**
     * Стабильный код класса.
     */
    val code: String,

    /**
     * Код стартового узла дерева навыков, с которого класс начинает прокачку.
     */
    val startNodeCode: String,

    /**
     * База на первом уровне.
     */
    val baseStats: MutableList<StatValue> = mutableListOf(),

    /**
     * Прирост базы за каждый уровень после первого.
     */
    val perLevelStats: MutableList<StatValue> = mutableListOf(),

    /**
     * Постоянные модификаторы класса - прежде всего конверсии атрибутов
     * вида "+1 к здоровью за каждые 2 Силы".
     *
     * Значения фиксированы справочником, как у узлов дерева: тира у них нет.
     */
    val params: MutableList<Modifier> = mutableListOf(),

    override var _id: String = ObjectId().toHexString()
) : StockEntity {

    /**
     * База класса на указанном уровне.
     */
    fun baseOn(level: Int): Map<IntEnumStat, Double> {
        val steps = (level - 1).coerceAtLeast(0)
        val result = baseStats.associate { it.stat to it.value }.toMutableMap()

        perLevelStats.forEach { growth ->
            result[growth.stat] = (result[growth.stat] ?: 0.0) + growth.value * steps
        }

        return result
    }
}
