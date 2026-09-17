package ru.descend.features.poe.domain

import org.bson.types.ObjectId
import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.string

/** Что должен записать вызывающий после чистого перехода: одна единица [currencyId] списывается. */
data class PoeCraftTransition(val character: Character, val currencyId: String, val result: PoeResult)

/** Immutable transition, shared by HTTP persistence and tests. */
class PoeInventory(private val catalog: PoeCatalog, private val crafting: PoeCrafting) {
    /**
     * Чистый переход крафта. [currencyUnits] — сколько единиц нужной валюты есть у персонажа:
     * валюта не стакается, каждая единица хранится отдельным документом, поэтому само списание
     * (ровно одна единица) выполняет вызывающий в той же транзакции.
     */
    fun craft(character: Character, ownerId: String, request: CraftRequest, currencyUnits: Long): PoeCraftTransition {
        require(character.userId == ownerId && !character.deleted) { "Character does not belong to the authenticated user" }
        ru.descend.shared.http.checkVersion(character.version, request.expectedVersion)
        require(request.requestId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) { "Invalid requestId" }
        val instance = requireNotNull(character.equipments.singleOrNull { it.uuid == request.equipmentUuid }) { "Equipment UUID is not in this character's inventory" }
        val oldState = requireNotNull(instance.poe) { "Legacy equipment has no PoE state; explicit migration is required" }
        require(instance.equipmentId == PoeCatalog.stableId("base:${oldState.baseId}")) { "Equipment base mismatch" }
        val currencyId = currencyId(request.currency)
        require(currencyUnits > 0) { "Not enough ${request.currency.displayName}" }
        val state = crafting.apply(oldState, request.currency)
        val updated = fromState(state, if (request.currency == PoeCurrency.MIRROR) ObjectId().toHexString() else instance.uuid).copy(baseSnapshot = instance.baseSnapshot ?: catalog.equipment(state.baseId))
        val equipments = character.equipments.toMutableList()
        if (request.currency == PoeCurrency.MIRROR) equipments += updated
        else equipments[equipments.indexOf(instance)] = updated
        val next = character.copy(equipments = equipments)
        return PoeCraftTransition(next, currencyId, PoeResult(request.requestId, character.version + 1, updated, currencyUnits - 1))
    }
    fun fromState(state: PoeItem, uuid: String = ObjectId().toHexString()) = CharacterEquipments(
        equipmentId = PoeCatalog.stableId("base:${state.baseId}"),
        params = (state.implicits.map { catalog.legacyModifier(it, true) } + state.explicits.map { catalog.legacyModifier(it) }).toMutableList(),
        uuid = uuid, poe = state, baseSnapshot = catalog.equipment(state.baseId))
    fun currencyId(currency: PoeCurrency): String {
        val id = requireNotNull(catalog.bases.entries.firstOrNull { it.value.string("name") == currency.displayName && it.value.string("item_class") == "StackableCurrency" }) { "Currency missing from catalog" }.key
        return PoeCatalog.stableId("base:$id")
    }
}
