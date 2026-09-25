package features.data.character.character_data

import application.enums.EnumSkillNodeType
import features.logic.modifiers.Modifier
import features.logic.skilltree.SkillTreeNode
import kotlinx.serialization.Serializable

/**
 * Узел дерева навыков, взятый персонажем: снимок узла на момент взятия.
 *
 * Лежит прямо в документе персонажа, отдельной коллекции у него нет.
 *
 * Снимок, а не ссылка, нужен ради правок под конкретного героя: значения
 * бонусов можно менять у одного персонажа, не трогая дерево и остальных.
 * Поэтому копируется всё, что у узла может отличаться от героя к герою -
 * бонусы, стоимость, название, вид.
 *
 * Форма дерева здесь намеренно не копируется: связи [SkillTreeNode.connections]
 * и координаты - это устройство мира, общее для всех. Проверки соседства и
 * целостности всегда идут по актуальному дереву, иначе у персонажа остался бы
 * путь, которого в дереве уже нет.
 */
@Serializable
data class CharacterSkillNode(

    /**
     * Код узла - ссылка на [SkillTreeNode.code]. Им узел ищется в дереве.
     */
    val code: String,

    /**
     * Снимок бонусов узла. Меняется под конкретного героя.
     *
     * [Modifier] неизменяем, поэтому правка - это замена элемента списка:
     * `params[i] = params[i].copy(values = ...)`. Дерево при этом не страдает.
     */
    var params: MutableList<Modifier> = mutableListOf(),

    /**
     * Снимок вида узла.
     */
    var type: EnumSkillNodeType = EnumSkillNodeType.SMALL,

    /**
     * Снимок стоимости: именно столько очков персонаж за узел заплатил
     * и именно столько вернёт при откате.
     */
    var cost: Int = 1,

    /**
     * Выбранный вариант мастерства или атрибутного узла (с 0.52.0) - индекс в
     * [SkillTreeNode.options]; его бонусы и лежат в [params]. У остальных узлов null.
     */
    var choice: Int? = null,
) {

    companion object {
        /**
         * Снимает узел дерева таким, какой он сейчас.
         */
        fun fromNode(node: SkillTreeNode, choice: Int? = null): CharacterSkillNode = CharacterSkillNode(
            code = node.code,
            // Modifier неизменяем, поэтому хватает копии списка; у узла с выбором - копии варианта
            params = (choice?.let { node.options[it] } ?: node.params).toMutableList(),
            type = node.type,
            cost = node.cost,
            choice = choice,
        )
    }
}
