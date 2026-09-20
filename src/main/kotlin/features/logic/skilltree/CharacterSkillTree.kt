package features.logic.skilltree

import application.enums.EnumSkillNodeType
import base.exception.model.SkillTreeExceptions
import kotlinx.serialization.Serializable

/**
 * Состояние дерева навыков персонажа.
 *
 * @property total сколько очков персонажу доступно на его уровне
 * @property spent сколько уже потрачено
 * @property nodes взятые узлы - такими, какие они в дереве прямо сейчас
 */
@Serializable
data class CharacterSkillTreeState(
    val characterId: String,
    val total: Int,
    val spent: Int,
    val available: Int,
    val nodes: List<SkillTreeNode>,
)

/**
 * Правила прокачки дерева навыков.
 *
 * Взяты из POE: начинают со стартового узла класса, дальше берут только
 * соседей уже взятых, а откатить узел можно лишь тогда, когда остальное
 * дерево не повиснет в воздухе.
 *
 * Логика чистая - работает на наборе узлов и списке взятых кодов,
 * поэтому проверяется тестами без Mongo.
 */
object SkillTreeAllocation {

    /**
     * Проверяет, что персонаж может взять узел.
     *
     * @param nodes всё дерево
     * @param node узел, который берут
     * @param taken коды уже взятых узлов
     * @param startNodeCode стартовый узел класса персонажа
     * @param available сколько очков у персонажа осталось
     * @throws SkillTreeExceptions.SkillTreeException если узел брать нельзя
     */
    fun requireAllocatable(
        nodes: Collection<SkillTreeNode>,
        node: SkillTreeNode,
        taken: Collection<String>,
        startNodeCode: String,
        available: Int,
    ) {
        if (node.code in taken)
            throw SkillTreeExceptions.funExceptionAlreadyTaken("allocate", node.code)

        if (node.type == EnumSkillNodeType.START) {
            // Стартовый узел персонаж получает при создании, второго не бывает
            if (taken.isNotEmpty())
                throw SkillTreeExceptions.funExceptionStartTaken("allocate", taken.first())
            if (node.code != startNodeCode)
                throw SkillTreeExceptions.funExceptionWrongStart("allocate", "${node.code}, class starts at $startNodeCode")
        } else {
            if (taken.isEmpty())
                throw SkillTreeExceptions.funExceptionNoStart("allocate", node.code)
            if (!SkillTreeGraph.isAdjacentTo(nodes, node.code, taken))
                throw SkillTreeExceptions.funExceptionNotConnected("allocate", node.code)
        }

        if (node.cost > available)
            throw SkillTreeExceptions.funExceptionNoPoints("allocate", "need ${node.cost}, available $available")
    }

    /**
     * Проверяет, что персонаж может откатить узел.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел откатить нельзя
     */
    fun requireRefundable(
        nodes: Collection<SkillTreeNode>,
        node: SkillTreeNode,
        taken: Collection<String>,
    ) {
        if (node.code !in taken)
            throw SkillTreeExceptions.funExceptionNotTaken("refund", node.code)

        // Без стартового узла дерево теряет корень: он уходит только полным сбросом
        if (node.type == EnumSkillNodeType.START)
            throw SkillTreeExceptions.funExceptionStartRefund("refund", node.code)

        if (!SkillTreeGraph.isConnected(nodes, taken.filterNot { it == node.code }))
            throw SkillTreeExceptions.funExceptionWouldDetach("refund", node.code)
    }

    /**
     * Сколько очков стоят взятые узлы.
     *
     * Коды, которых в дереве уже нет, не считаются: за них платить не с чего.
     */
    fun spent(nodes: Collection<SkillTreeNode>, taken: Collection<String>): Int {
        val byCode = nodes.associateBy { it.code }
        return taken.sumOf { byCode[it]?.cost ?: 0 }
    }
}
