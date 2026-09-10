package features.poe

import com.mongodb.client.model.Filters
import config.MongoFactory.transactionExecute
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.logic.modifiers.*
import kotlinx.coroutines.flow.toList

/** Add references only after their definitions exist. Each template and its legacy instances
 * migrate atomically. Conflicting item-local IDs are preserved under an item namespace. */
class ModifierReferenceMigration(private val equipment: EquipmentRepository,
    private val characters: CharacterRepository, private val definitions: ModifierDefinitionRepository) {
    suspend fun migrate() {
        definitions.ensureRevisionIndex()
        val candidates = equipment.findAll().filter {
            !it.modifierDefinitions.isNullOrEmpty() || !it.modifierDefinitionsStock.isNullOrEmpty()
        }
        for (candidate in candidates) transactionExecute("modifier.references.migrate") { session ->
            val item = requireNotNull(equipment.findById(candidate._id, session))
            (item.modifierDefinitions.orEmpty() + item.modifierDefinitionsStock.orEmpty()).groupBy { it.id }.forEach { (id, values) ->
                require(values.map { it.copy(_id = "") }.distinct().size == 1) { "Ambiguous embedded definition: $id" }
            }
            val embedded = (item.modifierDefinitions.orEmpty() + item.modifierDefinitionsStock.orEmpty()).distinctBy { it.id }
            val refs = mutableMapOf<String, ModifierRef>()
            for (definition in embedded) {
                var target = definition
                val existing = definitions.resolve(ModifierRef(definition.id, definition.revision), session)
                val same = existing == null || if (definition.poe != null) existing.poe?.canonicalDefinitionJson() == definition.poe.canonicalDefinitionJson()
                    else existing.copy(_id = "") == definition.copy(_id = "")
                if (!same) target = definition.copy(id = "legacy/${item._id}/${definition.id}")
                val ref = ModifierRef(target.id, target.revision)
                val stored = definitions.resolve(ref, session)
                if (stored == null) definitions.insert(target.copy(_id = PoeCatalog.stableId("modifier:${target.id}:${target.revision}")), session)
                else require(same || stored.copy(_id = "") == target.copy(_id = "")) { "Conflicting migration target: $ref" }
                refs[definition.id] = ref
            }
            fun remap(modifier: Modifier): Modifier = refs[modifier.definitionId]?.let {
                modifier.copy(definitionId = it.definitionId, definitionRevision = it.revision)
            } ?: modifier
            item.modifierDefinitionRefs = (item.modifierDefinitionRefs + item.modifierDefinitions.orEmpty().map { refs.getValue(it.id) }).distinct()
            item.stockModifierDefinitionRefs = (item.stockModifierDefinitionRefs + item.modifierDefinitionsStock.orEmpty().map { refs.getValue(it.id) }).distinct()
            item.modifierDefinitions = null
            item.modifierDefinitionsStock = null
            item.modifiers = item.modifiers?.map(::remap)?.let { ArrayList(it) }
            val owners = characters.collection.find(session, Filters.eq("equipments.equipmentId", item._id)).toList()
            owners.forEach { owner ->
                val next = owner.copy(equipments = owner.equipments.map { instance ->
                    if (instance.equipmentId == item._id && instance.poe == null) instance.copy(params = instance.params.map(::remap).toMutableList()) else instance
                }.toMutableList())
                if (next.equipments != owner.equipments) characters.update(next, session)
            }
            equipment.update(item, session)
        }
    }
}
