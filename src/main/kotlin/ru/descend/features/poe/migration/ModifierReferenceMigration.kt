package ru.descend.features.poe.migration

import com.mongodb.client.model.Filters
import kotlinx.coroutines.flow.toList
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.ModifierRef
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.modifiers.persistence.ModifierDefinitionRepository
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.canonicalDefinitionJson
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute

/** Add references only after their definitions exist. Each template and its legacy instances
 * migrate atomically. Conflicting item-local IDs are preserved under an item namespace. */
class ModifierReferenceMigration(private val equipment: EquipmentRepository,
    private val definitions: ModifierDefinitionRepository,
    private val equipmentItems: ru.descend.features.character.persistence.CharacterEquipmentRepository) {
    private companion object { const val BATCH = 200 }

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
            // Экипировка живёт отдельными документами, поэтому экземпляры шаблона обходятся страницами
            // по _id: миграция не зависит от размера инвентаря отдельного персонажа.
            var after: String? = null
            while (true) {
                val filter = Filters.and(Filters.eq("item.equipmentId", item._id),
                    if (after == null) Filters.empty() else Filters.gt("_id", after))
                val batch = equipmentItems.collection.find(session, filter)
                    .sort(com.mongodb.client.model.Sorts.ascending("_id")).limit(BATCH).toList()
                if (batch.isEmpty()) break
                batch.filter { it.item.poe == null }.forEach { row ->
                    val next = row.item.copy(params = row.item.params.map(::remap).toMutableList())
                    if (next != row.item) equipmentItems.replace(row.characterId, next, session)
                }
                if (batch.size < BATCH) break
                after = batch.last()._id
            }
            equipment.update(item, session)
        }
    }
}
