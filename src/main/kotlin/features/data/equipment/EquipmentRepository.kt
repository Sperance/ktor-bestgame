package features.data.equipment

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentCache
import features.data.equipment.equipment_data.Equipment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EquipmentRepository : BaseRepository<Equipment>(entityClass = Equipment::class), KoinComponent {
    private val definitions: features.logic.modifiers.ModifierDefinitionRepository by inject()
    private val equipmentCache: EquipmentCache by inject()

    override suspend fun validateBeforeInsert(entity: Equipment, session: ClientSession) {
        require(entity.modifierDefinitions.isNullOrEmpty() && entity.modifierDefinitionsStock.isNullOrEmpty()) {
            "Publish definitions separately and use modifierDefinitionRefs / stockModifierDefinitionRefs"
        }
        (entity.modifierDefinitionRefs + entity.stockModifierDefinitionRefs).forEach {
            requireNotNull(definitions.resolve(it, session)) { "Unknown modifier reference: $it" }
        }
    }

    override suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        changes.keys.forEach { key ->
            if (key.substringBefore('.') in setOf("modifierDefinitions", "modifierDefinitionsStock")) {
                require(changes[key] == null || changes[key] == emptyList<Any>()) { "Embedded definitions are no longer accepted" }
            }
            if (key.startsWith("modifierDefinitionRefs.") || key.startsWith("stockModifierDefinitionRefs.")) {
                throw IllegalArgumentException("Replace the reference list as a whole")
            }
        }
        listOf("modifierDefinitionRefs", "stockModifierDefinitionRefs").forEach { field ->
            if (field in changes) {
                val value = changes[field]
                require(value is List<*>) { "Expected a list of references" }
                value.forEach { entry ->
                    val ref = if (entry is features.logic.modifiers.ModifierRef) entry else {
                        require(entry is Map<*, *>)
                        val id = entry["definitionId"] as? String ?: throw IllegalArgumentException("Missing definitionId")
                        require("revision" !in entry || entry["revision"] is Number) { "Expected integer revision" }
                        val revision = entry["revision"] as? Number
                        require(revision == null || revision.toDouble() == revision.toInt().toDouble())
                        features.logic.modifiers.ModifierRef(id, revision?.toInt() ?: 1)
                    }
                    requireNotNull(definitions.resolve(ref)) { "Unknown modifier reference: $ref" }
                }
            }
        }
    }

    override suspend fun validateAfterInsert(entity: Equipment, session: ClientSession) {
        equipmentCache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: Equipment, session: ClientSession, softDelete: Boolean) {
        equipmentCache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: Equipment, session: ClientSession) {
        equipmentCache.updateItem(entity)
    }
}