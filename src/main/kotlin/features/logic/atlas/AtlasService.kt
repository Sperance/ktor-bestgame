package features.logic.atlas

import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.data.character.Character
import features.data.character.CharacterRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Атлас героя: [allocated] - взятые узлы, корень первым; [earned] - засчитанные достижения
 * (`exit:<карта>`...), [points] - сколько очков они принесли, [available] - сколько ещё не потрачено.
 */
@Serializable
data class AtlasState(val allocated: List<String>, val earned: List<String>, val points: Int, val available: Int)

/**
 * Пассивное дерево атласа (с 0.60.0): взятие, откат за золото и полный сброс. Форма дерева и цены -
 * из [AtlasContent], взятое и заработанное лежит у героя.
 */
class AtlasService : KoinComponent {
    private val characters: CharacterRepository by inject()

    fun tree(): AtlasTree = AtlasContent.tree

    suspend fun state(characterId: String): AtlasState = stateOf(characters.requireCharacter(characterId, "atlasState"))

    suspend fun allocate(characterId: String, nodeCode: String): AtlasState {
        val method = "allocateAtlas"
        val character = characters.requireCharacter(characterId, method)
        AtlasAllocation.requireAllocatable(AtlasContent.graph, nodeCode, character.atlasNodes, stateOf(character).available, method)
        character.atlasNodes.add(nodeCode)
        transactionExecute("$method $nodeCode") { session -> characters.update(character, session) }
        return stateOf(character)
    }

    /** Откат одного узла стоит цену [AtlasRespec] за узел. */
    suspend fun refund(characterId: String, nodeCode: String): AtlasState {
        val method = "refundAtlas"
        val character = characters.requireCharacter(characterId, method)
        AtlasAllocation.requireRefundable(AtlasContent.graph, nodeCode, character.atlasNodes, method)
        charge(character, AtlasContent.tree.respec.price(character.level.toInt(), 1), method)
        character.atlasNodes.remove(nodeCode)
        transactionExecute("$method $nodeCode") { session -> characters.update(character, session) }
        return stateOf(character)
    }

    /** Полный сброс стоит столько же, сколько поузловой откат всего взятого: выбор удобства, а не цены. */
    suspend fun reset(characterId: String): AtlasState {
        val method = "resetAtlas"
        val character = characters.requireCharacter(characterId, method)
        if (character.atlasNodes.isEmpty()) return stateOf(character)
        charge(character, AtlasContent.tree.respec.price(character.level.toInt(), character.atlasNodes.size), method)
        character.atlasNodes.clear()
        transactionExecute(method) { session -> characters.update(character, session) }
        return stateOf(character)
    }

    private fun charge(character: Character, price: Long, method: String) {
        if (character.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        character.money -= price
    }

    private fun stateOf(character: Character): AtlasState {
        val rule = AtlasContent.tree.points
        return AtlasState(listOf(AtlasContent.graph.start) + character.atlasNodes, character.atlasEarned.toList(),
            AtlasPoints.total(rule, character.atlasEarned), AtlasPoints.available(rule, character.atlasEarned, character.atlasNodes))
    }
}
