package ru.descend.features.poe.application

import kotlin.random.Random
import kotlinx.serialization.encodeToString
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.int
import ru.descend.features.poe.domain.CraftRequest
import ru.descend.features.poe.domain.DropRequest
import ru.descend.features.poe.domain.PoeCrafting
import ru.descend.features.poe.domain.PoeInventory
import ru.descend.features.poe.domain.PoeRarity
import ru.descend.features.poe.domain.PoeResult
import ru.descend.features.poe.persistence.MongoModifierCatalog
import ru.descend.features.poe.persistence.PoeReceipt
import ru.descend.features.poe.persistence.ReceiptRepository
import ru.descend.infrastructure.http.AppJson
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute

/**
 * Крафт и дроп пишут предмет отдельным документом, а персонажа обновляют только ради CAS по version.
 * Обе записи идут в одной транзакции, поэтому атомарность списания валюты и изменения предмета сохранена.
 */
class PoeService(private val characters: CharacterRepository, private val equipment: EquipmentRepository,
    private val catalogs: MongoModifierCatalog,
    private val receipts: ReceiptRepository,
    private val equipmentService: ru.descend.features.character.application.EquipmentService,
    private val equipmentItems: ru.descend.features.character.persistence.CharacterEquipmentRepository) {

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
            // Поднимается только рабочий набор: надетое плюс сам крафтимый предмет.
            val loaded = equipmentService.hydrate(character, listOf(request.equipmentUuid), session)
            val required = loaded.equipments.flatMap { instance ->
                instance.poe?.let { state -> (state.implicits + state.explicits).map { ru.descend.domain.modifiers.ModifierRef(it.id, it.revision) } }.orEmpty()
            }
            val catalog = catalogs.snapshot(required).catalog
            val inventory = PoeInventory(catalog, PoeCrafting(catalog))
            val (next, result) = inventory.craft(loaded, ownerId, request)
            // CAS on the character version covers both the item and the currency stack.
            equipmentService.validate(next, session)
            // Mirror копирует предмет в новый документ, остальные валюты меняют его на месте.
            if (request.currency == ru.descend.features.poe.domain.PoeCurrency.MIRROR) equipmentItems.add(character._id, result.equipment, session)
            else equipmentItems.replace(character._id, result.equipment, session)
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
            ru.descend.shared.http.checkVersion(character.version, request.expectedVersion)
            val catalog = catalogs.snapshot().catalog
            val crafting = PoeCrafting(catalog)
            val inventory = PoeInventory(catalog, crafting)
            val level = character.level.toInt().coerceIn(1, 100)
            val baseId = catalog.bases.filterValues { catalog.ordinaryDrop(it) && it.int("drop_level", 1) <= level }.keys.random()
            val template = requireNotNull(equipment.findById(PoeCatalog.stableId("base:$baseId"), session)) { "Base not seeded" }
            val rarity = if (catalog.unique(catalog.base(baseId))) PoeRarity.UNIQUE else when (Random.nextInt(100)) { in 0..49 -> PoeRarity.NORMAL; in 50..84 -> PoeRarity.MAGIC; else -> PoeRarity.RARE }
            val instance = inventory.fromState(crafting.generate(baseId, level, rarity, template.stockModifierDefinitionRefs))
            // Инвентарь не ограничен: новый предмет — это новый документ коллекции.
            equipmentItems.add(character._id, instance, session)
            val next = equipmentService.hydrate(character, session = session)
            equipmentService.validate(next, session)
            characters.update(next, session)
            val result = PoeResult(request.requestId, next.version, instance)
            receipts.insert(PoeReceipt(key, payload, result), session)
            result
        }
}
