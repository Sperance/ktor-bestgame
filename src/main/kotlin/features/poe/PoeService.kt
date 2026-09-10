package features.poe

import base.entity.StockEntity
import base.repository.BaseRepository
import config.MongoFactory.transactionExecute
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import server.addons.AppJson
import kotlin.random.Random

@Serializable
data class PoeReceipt(override var _id: String, val payload: String, val result: PoeResult) : StockEntity
private class ReceiptRepository : BaseRepository<PoeReceipt>(PoeReceipt::class)

class PoeService(private val characters: CharacterRepository, private val equipment: EquipmentRepository,
    private val catalogs: MongoModifierCatalog) {
    private val receipts = ReceiptRepository()

    suspend fun craft(characterId: String, ownerId: String, request: CraftRequest): PoeResult =
        transactionExecute("poe.craft") { session ->
            val character = requireNotNull(characters.findById(characterId, session)) { "Character not found" }
            require(character.userId == ownerId && !character.deleted) { "Character does not belong to authenticated user" }
            val key = PoeCatalog.stableId("$ownerId:$characterId:${request.requestId}")
            val payload = "craft:" + AppJson.encodeToString(request)
            receipts.findById(key, session)?.let {
                require(it.payload == payload) { "requestId has already been used for a different request" }
                return@transactionExecute it.result
            }
            val required = character.equipments.flatMap { instance ->
                instance.poe?.let { state -> (state.implicits + state.explicits).map { features.logic.modifiers.ModifierRef(it.id, it.revision) } }.orEmpty()
            }
            val catalog = catalogs.snapshot(required).catalog
            val inventory = PoeInventory(catalog, PoeCrafting(catalog))
            val (next, result) = inventory.craft(character, ownerId, request)
            // CAS on the character version covers both the item and the currency stack.
            characters.update(next, session)
            receipts.insert(PoeReceipt(key, payload, result), session)
            result
        }

    /** Administrative test drop. Gameplay rewards should call this from a verified server event. */
    suspend fun drop(characterId: String, ownerId: String, request: DropRequest): PoeResult =
        transactionExecute("poe.drop") { session ->
            val character = requireNotNull(characters.findById(characterId, session)) { "Character not found" }
            require(character.userId == ownerId && !character.deleted) { "Character does not belong to authenticated user" }
            require(request.requestId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid requestId" }
            val key = PoeCatalog.stableId("$ownerId:$characterId:${request.requestId}")
            val payload = "drop:" + AppJson.encodeToString(request)
            receipts.findById(key, session)?.let {
                require(it.payload == payload) { "requestId has already been used for a different request" }
                return@transactionExecute it.result
            }
            require(character.version == request.expectedVersion) { "Stale character version; reload inventory" }
            val catalog = catalogs.snapshot().catalog
            val crafting = PoeCrafting(catalog)
            val inventory = PoeInventory(catalog, crafting)
            val level = character.level.toInt().coerceIn(1, 100)
            val baseId = catalog.bases.filterValues { catalog.ordinaryDrop(it) && it.int("drop_level", 1) <= level }.keys.random()
            val template = requireNotNull(equipment.findById(PoeCatalog.stableId("base:$baseId"), session)) { "Base not seeded" }
            val rarity = when (Random.nextInt(100)) { in 0..49 -> PoeRarity.NORMAL; in 50..84 -> PoeRarity.MAGIC; else -> PoeRarity.RARE }
            val instance = inventory.fromState(crafting.generate(baseId, level, rarity, template.stockModifierDefinitionRefs))
            val next = character.copy(equipments = (character.equipments + instance).toMutableList())
            characters.update(next, session)
            val result = PoeResult(request.requestId, next.version, instance)
            receipts.insert(PoeReceipt(key, payload, result), session)
            result
        }
}
