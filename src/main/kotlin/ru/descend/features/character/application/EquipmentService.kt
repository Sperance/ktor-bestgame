package ru.descend.features.character.application

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import ru.descend.domain.modifiers.ModifierRef
import ru.descend.features.character.model.*
import ru.descend.features.character.domain.*
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.poe.persistence.MongoModifierCatalog
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.http.*

@Serializable data class EquipmentView(val characterVersion: Long, val equipped: Map<EquipmentSlot, String>, val inventory: List<CharacterEquipments>, val items: List<CharacterItems>, val stats: CharacterStats)
class EquipmentService(private val characters: CharacterRepository, private val equipment: EquipmentRepository, private val catalogs: MongoModifierCatalog) {
    suspend fun context(character: Character): Pair<Map<String, Equipment>, MongoModifierCatalog.Snapshot> {
        val refs = character.params.map { ModifierRef(it.definitionId, it.definitionRevision) } + character.equipments.flatMap { item ->
            item.poe?.let { p -> (p.implicits + p.explicits).map { ModifierRef(it.id, it.revision) } } ?: item.params.map { ModifierRef(it.definitionId, it.definitionRevision) }
        }
        val templates = if (character.equipments.isEmpty()) emptyMap() else equipment.collection.find(Filters.`in`("_id", character.equipments.map { it.equipmentId })).toList().associateBy { it._id }
        return templates to catalogs.snapshot(refs)
    }
    suspend fun validate(character: Character) {
        val (templates, snapshot) = context(character)
        EquipmentRules.validate(character, templates, snapshot.catalog) { c -> CharacterStatsCalculator().calculate(c, templates, snapshot.catalog, snapshot.definitions) }
    }
    suspend fun view(characterId: String, actor: Actor): EquipmentView {
        val character = characters.findById(checkedId(characterId)) ?: missing(); actor.own(character)
        return view(character)
    }
    suspend fun view(character: Character): EquipmentView {
        val (templates, snapshot) = context(character)
        return EquipmentView(character.version, character.equipped, character.equipments, character.items, CharacterStatsCalculator().calculate(character, templates, snapshot.catalog, snapshot.definitions))
    }
    suspend fun equip(id: String, actor: Actor, command: EquipCommand): EquipmentView = change(id, actor, command.expectedVersion) { character, _ ->
        if (character.equipments.none { it.uuid == command.equipmentUuid }) missing()
        character.copy(equipped = character.equipped.filterValues { it != command.equipmentUuid } + (command.slot to command.equipmentUuid))
    }
    suspend fun unequip(id: String, actor: Actor, command: UnequipCommand): EquipmentView = change(id, actor, command.expectedVersion) { character, _ ->
        character.copy(equipped = character.equipped - command.slot)
    }
    suspend fun grant(id: String, actor: Actor, command: GrantEquipmentCommand): EquipmentView {
        actor.requireAdmin()
        return change(id, actor, command.expectedVersion) { character, session ->
            if (character.equipments.size >= 500) invalid("Inventory is full")
            val base = equipment.findById(checkedId(command.equipmentId), session)?.takeUnless { it.deleted } ?: missing()
            val refs = base.modifierDefinitionRefs + base.stockModifierDefinitionRefs
            val snapshot = catalogs.snapshot(refs)
            val instance = CharacterEquipments.fromEquipment(base, snapshot.catalog, refs.map(snapshot::resolve))
            character.copy(equipments = (character.equipments + instance).toMutableList())
        }
    }
    suspend fun change(id: String, actor: Actor, expectedVersion: Long, body: suspend (Character, ClientSession) -> Character): EquipmentView {
        val changed = transactionExecute("character.command") { session ->
            val old = characters.findById(checkedId(id), session) ?: missing(); actor.own(old)
            checkVersion(old.version, expectedVersion)
            val next = body(old, session)
            validate(next)
            characters.update(next, session)
            next
        }
        return view(changed)
    }
}
