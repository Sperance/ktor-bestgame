package features.logic.skilltree

import application.enums.EnumSkillNodeType
import application.enums.IntEnumStat
import base.exception.model.SkillTreeExceptions
import features.data.character.character_data.CharacterSkillNode
import features.logic.modifiers.StatContribution
import kotlinx.serialization.Serializable

/**
 * Состояние дерева навыков персонажа.
 *
 * @property total сколько очков персонажу доступно на его уровне
 * @property spent сколько уже потрачено
 * @property nodes взятые узлы - снимками, то есть с личными значениями героя
 */
@Serializable
data class CharacterSkillTreeState(
    val characterId: String,
    val total: Int,
    val spent: Int,
    val available: Int,
    val nodes: List<CharacterSkillNode>,

    /**
     * Что даёт взятое дерево - по строке на характеристику и вид операции.
     *
     * Это вклад дерева, а не итог персонажа: база в него не входит, поэтому
     * "+40% к физическому урону" остаётся процентом. Раньше здесь был итог от
     * пустой базы, и все проценты пропадали - 0 * 1.4 это ноль, а ноль
     * отбрасывался.
     *
     * Считает сервер, а не клиент, и не для строгости: у модификаторов есть
     * операции, и два INCREASED складываются, а два MORE перемножаются.
     * Сложение снимков на клиенте врало бы там, где игрок решает, стоит ли
     * узел очка.
     */
    val totals: List<StatContribution> = emptyList(),
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
     * @param graph всё дерево
     * @param node узел, который берут
     * @param taken коды уже взятых узлов
     * @param startNodeCode стартовый узел класса персонажа
     * @param available сколько очков у персонажа осталось
     * @param choice выбранный вариант - обязателен у мастерства и атрибутного узла, у прочих запрещён
     * @throws SkillTreeExceptions.SkillTreeException если узел брать нельзя
     */
    fun requireAllocatable(
        graph: SkillTreeGraph,
        node: SkillTreeNode,
        taken: Collection<String>,
        startNodeCode: String,
        available: Int,
        choice: Int? = null,
    ) {
        if (node.options.isEmpty() != (choice == null) || (choice != null && choice !in node.options.indices))
            throw SkillTreeExceptions.funExceptionChoice("allocate", "${node.code}: $choice of ${node.options.size}")

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
            if (!graph.isAdjacentTo(node.code, taken))
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
        graph: SkillTreeGraph,
        node: SkillTreeNode,
        taken: Collection<String>,
    ) {
        if (node.code !in taken)
            throw SkillTreeExceptions.funExceptionNotTaken("refund", node.code)

        // Без стартового узла дерево теряет корень: он уходит только полным сбросом
        if (node.type == EnumSkillNodeType.START)
            throw SkillTreeExceptions.funExceptionStartRefund("refund", node.code)

        if (!graph.isConnected(taken.filterNot { it == node.code }))
            throw SkillTreeExceptions.funExceptionWouldDetach("refund", node.code)
    }

    /**
     * Проверяет, что гнездо пусто.
     *
     * Вернуть гнездо с самоцветом внутри нельзя: иначе камень остался бы висеть
     * в узле, которого у персонажа больше нет. Сервер его не вынимает сам -
     * это вещь игрока, и решать, куда её деть, ему.
     *
     * @param socketed коды гнёзд, в которых сейчас сидят самоцветы
     * @throws SkillTreeExceptions.SkillTreeException если гнездо занято
     */
    fun requireSocketEmpty(node: SkillTreeNode, socketed: Collection<String>) {
        if (node.type == EnumSkillNodeType.JEWEL_SOCKET && node.code in socketed)
            throw SkillTreeExceptions.funExceptionSocketBusy("refund", node.code)
    }
}
