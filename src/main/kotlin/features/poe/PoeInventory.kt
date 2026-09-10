package features.poe

import features.data.character.Character
import features.data.character.character_data.CharacterEquipments
import features.data.character.character_data.CharacterItems
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class CraftRequest(val requestId: String, val equipmentUuid: String, val currency: PoeCurrency, val expectedVersion: Long)
@Serializable
data class DropRequest(val requestId: String, val expectedVersion: Long)
@Serializable
data class PoeResult(val requestId: String, val characterVersion: Long, val equipment: CharacterEquipments, val currencyRemaining: Long? = null)

/** Immutable transition, shared by HTTP persistence and tests. */
class PoeInventory(private val catalog: PoeCatalog, private val crafting: PoeCrafting) {
    fun craft(character: Character, ownerId: String, request: CraftRequest): Pair<Character, PoeResult> {
        require(character.userId == ownerId && !character.deleted) { "Character does not belong to the authenticated user" }
        require(character.version == request.expectedVersion) { "Stale character version; reload inventory" }
        require(request.requestId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid requestId" }
        val instance = requireNotNull(character.equipments.singleOrNull { it.uuid == request.equipmentUuid }) { "Equipment UUID is not in this character's inventory" }
        val oldState = requireNotNull(instance.poe) { "Legacy equipment has no PoE state; explicit migration is required" }
        require(instance.equipmentId == PoeCatalog.stableId("base:${oldState.baseId}")) { "Equipment base mismatch" }
        val currencyId = currencyId(request.currency)
        val currencyStack = requireNotNull(character.items.singleOrNull { it.itemId == currencyId && it.amount > 0 }) { "Not enough ${request.currency.displayName}" }
        val state = crafting.apply(oldState, request.currency)
        val updated = fromState(state, if (request.currency == PoeCurrency.MIRROR) ObjectId().toHexString() else instance.uuid)
        val equipments = character.equipments.toMutableList()
        if (request.currency == PoeCurrency.MIRROR) equipments += updated
        else equipments[equipments.indexOf(instance)] = updated
        val amount = currencyStack.amount - 1
        val inventory = character.items.filterNot { it.itemId == currencyId }.toMutableList()
        if (amount > 0) inventory += CharacterItems(currencyId, amount)
        val next = character.copy(equipments = equipments, items = inventory)
        return next to PoeResult(request.requestId, character.version + 1, updated, amount)
    }
    fun fromState(state: PoeItem, uuid: String = ObjectId().toHexString()) = CharacterEquipments(
        equipmentId = PoeCatalog.stableId("base:${state.baseId}"),
        params = (state.implicits.map { catalog.legacyModifier(it, true) } + state.explicits.map { catalog.legacyModifier(it) }).toMutableList(),
        uuid = uuid, poe = state)
    fun currencyId(currency: PoeCurrency): String {
        val id = requireNotNull(catalog.bases.entries.firstOrNull { it.value.string("name") == currency.displayName && it.value.string("item_class") == "StackableCurrency" }) { "Currency missing from catalog" }.key
        return PoeCatalog.stableId("base:$id")
    }
}
