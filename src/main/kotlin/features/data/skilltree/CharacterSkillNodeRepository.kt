package features.data.skilltree

import application.enums.EnumSkillNodeType
import base.exception.model.CharacterExceptions
import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import features.caches.SkillTreeCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.logic.progression.CharacterClass
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Прокачка дерева навыков персонажем.
 *
 * Правила взяты из POE: начинают со стартового узла класса, дальше берут
 * только соседей уже взятых узлов, а откатить узел можно лишь тогда,
 * когда остальное дерево не повиснет в воздухе.
 */
class CharacterSkillNodeRepository : BaseRepository<CharacterSkillNode>(
    entityClass = CharacterSkillNode::class
), KoinComponent {
    private val treeCache: SkillTreeCache by inject()
    private val characterRepository: CharacterRepository by inject()

    init {
        initialize(indexedFields = listOf("characterId", "nodeCode"))
    }

    /**
     * Все взятые персонажем узлы.
     */
    suspend fun findByCharacter(characterId: String): List<CharacterSkillNode> =
        findByFilter(Filters.eq("characterId", characterId))

    /**
     * Состояние дерева персонажа: взятые узлы и баланс очков.
     */
    suspend fun stateOf(characterId: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "stateOf")
        val nodes = findByCharacter(characterId)
        val spent = nodes.sumOf { it.cost }

        return CharacterSkillTreeState(
            characterId = characterId,
            total = characterRepository.skillPointsTotal(character),
            spent = spent,
            available = characterRepository.skillPointsTotal(character) - spent,
            nodes = nodes
        )
    }

    /**
     * Берёт узел дерева. Бонусы узла запоминаются снимком.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел брать нельзя
     */
    suspend fun allocate(characterId: String, nodeCode: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "allocate")
        val node = treeCache.findByCode(nodeCode)
            ?: throw SkillTreeExceptions.funExceptionNodeNotFound("allocate", nodeCode)

        val taken = findByCharacter(characterId)
        if (taken.any { it.nodeCode == nodeCode })
            throw SkillTreeExceptions.funExceptionAlreadyTaken("allocate", nodeCode)

        val takenCodes = taken.map { it.nodeCode }
        if (node.type == EnumSkillNodeType.START) {
            if (takenCodes.isNotEmpty())
                throw SkillTreeExceptions.funExceptionStartTaken("allocate", takenCodes.first())

            // Начать можно только со стартового узла своего класса
            val startNodeCode = characterRepository.requireClass(character).startNodeCode
            if (node.code != startNodeCode)
                throw SkillTreeExceptions.funExceptionWrongStart("allocate", "${node.code}, class starts at $startNodeCode")
        } else {
            if (takenCodes.isEmpty())
                throw SkillTreeExceptions.funExceptionNoStart("allocate", nodeCode)
            if (!treeCache.isAdjacentTo(nodeCode, takenCodes))
                throw SkillTreeExceptions.funExceptionNotConnected("allocate", nodeCode)
        }

        val available = characterRepository.skillPointsTotal(character) - taken.sumOf { it.cost }
        if (node.cost > available)
            throw SkillTreeExceptions.funExceptionNoPoints("allocate", "need ${node.cost}, available $available")

        transactionExecute("allocate $nodeCode") { session ->
            insert(CharacterSkillNode.fromNode(characterId, node), session)
        }

        return stateOf(characterId)
    }

    /**
     * Откатывает узел и возвращает очки.
     *
     * Стартовый узел откатывается только полным сбросом: без него дерево
     * теряет корень.
     */
    suspend fun refund(characterId: String, nodeCode: String): CharacterSkillTreeState {
        requireCharacter(characterId, "refund")

        val taken = findByCharacter(characterId)
        val allocated = taken.find { it.nodeCode == nodeCode }
            ?: throw SkillTreeExceptions.funExceptionNotTaken("refund", nodeCode)

        if (allocated.type == EnumSkillNodeType.START)
            throw SkillTreeExceptions.funExceptionStartRefund("refund", nodeCode)

        val remaining = taken.map { it.nodeCode }.filterNot { it == nodeCode }
        if (!treeCache.isConnected(remaining))
            throw SkillTreeExceptions.funExceptionWouldDetach("refund", nodeCode)

        transactionExecute("refund $nodeCode") { session ->
            deleteById(allocated, session)
        }

        return stateOf(characterId)
    }

    /**
     * Полный сброс дерева: все очки возвращаются персонажу.
     *
     * Стартовый узел класса выдаётся заново - как в POE, где полный
     * респек возвращает персонажа в точку старта его класса, а не
     * оставляет дерево вообще без корня.
     */
    suspend fun reset(characterId: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "reset")

        transactionExecute("reset skill tree") { session ->
            deleteByCharacter(characterId, session)
            allocateStart(character, characterRepository.requireClass(character), session)
        }

        return stateOf(characterId)
    }

    /**
     * Выдаёт персонажу стартовый узел его класса.
     *
     * В POE стартовый узел уже выделен в момент создания персонажа: он ничего
     * не стоит и служит корнем, от которого растёт всё остальное дерево.
     * Поэтому узел выдаётся прямо в транзакции создания персонажа, а не
     * отдельным запросом от клиента.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узла нет в дереве
     */
    suspend fun allocateStart(
        character: Character,
        characterClass: CharacterClass,
        session: ClientSession
    ): CharacterSkillNode {
        val node = treeCache.findByCode(characterClass.startNodeCode)
            ?: throw SkillTreeExceptions.funExceptionNodeNotFound("allocateStart", characterClass.startNodeCode)

        if (node.type != EnumSkillNodeType.START)
            throw SkillTreeExceptions.funExceptionWrongStart("allocateStart", node.code)

        return insert(CharacterSkillNode.fromNode(character._id, node), session)
    }

    /**
     * Выдаёт стартовые узлы тем персонажам, у которых их ещё нет.
     *
     * Нужно созданным до появления автовыдачи: без корня дерево не растёт.
     *
     * @return сколько персонажей получили стартовый узел
     */
    suspend fun ensureStartNodes(characters: List<Character>, session: ClientSession): Int {
        if (characters.isEmpty()) return 0

        val startCodes = treeCache.startNodes().map { it.code }.toSet()
        if (startCodes.isEmpty()) return 0

        val withStart = findByFilter(session, Filters.`in`("nodeCode", startCodes))
            .map { it.characterId }
            .toSet()

        var created = 0
        characters.filterNot { it._id in withStart }.forEach { character ->
            allocateStart(character, characterRepository.requireClass(character), session)
            created++
        }

        return created
    }

    /**
     * Удаляет всё дерево персонажа - вызывается при удалении самого персонажа.
     */
    suspend fun deleteByCharacter(characterId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("characterId", characterId))
    }

    /**
     * Удаляет взятые узлы, которых в дереве уже нет.
     *
     * Снимок бонусов переживает перебалансировку дерева, но удалённый
     * из дерева узел оставлять персонажу нельзя.
     *
     * @param nodeCodes коды всех существующих узлов
     * @return количество удалённых документов
     */
    suspend fun deleteByMissingNode(nodeCodes: Collection<String>, session: ClientSession): Long {
        if (nodeCodes.isEmpty()) return 0
        return collection.deleteMany(session, Filters.nin("nodeCode", nodeCodes)).deletedCount
    }

    private suspend fun requireCharacter(characterId: String, method: String): Character =
        characterRepository.findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)
}
