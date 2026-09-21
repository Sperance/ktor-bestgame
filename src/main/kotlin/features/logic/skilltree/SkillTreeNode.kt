package features.logic.skilltree

import application.enums.EnumSkillNodeType
import base.entity.StockEntity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Узел дерева навыков. Отдельная коллекция Mongo `SkillTreeNode`.
 *
 * Дерево - это граф: узел знает своих соседей по их кодам, а взять его
 * можно только рядом с уже взятым. Бонусы узла фиксированы и не роллятся,
 * поэтому лежат готовыми [Modifier] без тира.
 *
 * Координаты нужны клиенту для отрисовки и на правила не влияют.
 */
@Serializable
data class SkillTreeNode(

    /**
     * Стабильный код узла. Уникален в пределах коллекции,
     * именно им персонаж ссылается на взятый узел.
     */
    val code: String,

    val type: EnumSkillNodeType,

    /**
     * Бонусы узла. Значения фиксированы деревом, тира у них нет.
     */
    val params: MutableList<Modifier> = mutableListOf(),

    /**
     * Коды соседних узлов. Связь считается двусторонней:
     * достаточно объявить её с одной стороны.
     */
    val connections: MutableList<String> = mutableListOf(),

    /**
     * Сколько очков навыков стоит узел. Стартовый узел бесплатен.
     */
    val cost: Int = 1,

    val positionX: Int = 0,
    val positionY: Int = 0,

    override var _id: String = ObjectId().toHexString()
) : StockEntity
