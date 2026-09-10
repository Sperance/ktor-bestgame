package features.poe

import config.MongoFactory.transactionExecute
import features.data.equipment.EquipmentRepository
import features.data.items.Items
import features.data.items.ItemsRepository
import features.logic.modifiers.ModifierDefinitionRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Add missing, stable IDs in bounded transactions; never delete or rewrite player/catalog data.
 * Export includes legacy and non-drop data: only released wearables become Equipment templates.
 * Other released base types are catalogued as Items, not automatically granted as loot. */
object PoeSeeder : KoinComponent {
    private val equipment: EquipmentRepository by inject()
    private val items: ItemsRepository by inject()
    private val modifiers: ModifierDefinitionRepository by inject()
    suspend fun seed() {
        val catalog = PoeCatalog.bundled
        val existingEquipment = equipment.findAll().map { it._id }.toSet()
        catalog.releasedWearables().keys.sorted().filter { PoeCatalog.stableId("base:$it") !in existingEquipment }.chunked(100).forEach { batch ->
            transactionExecute("poe.seed.equipment") { session -> equipment.insertMany(batch.map(catalog::equipment), session) }
        }
        val existingItems = items.findAll().map { it._id }.toSet()
        catalog.bases.filterValues { !catalog.wearable(it) && it.string("release_state") == "released" }
            .filterKeys { PoeCatalog.stableId("base:$it") !in existingItems }.entries.chunked(100).forEach { batch ->
                transactionExecute("poe.seed.items") { session ->
                    items.insertMany(batch.map { (id, b) -> Items(name = b.string("name"), category = "POE", subCategory = b.string("item_class"),
                        description = "Catalog record; release state does not imply ordinary drop eligibility", poeBaseId = id, _id = PoeCatalog.stableId("base:$id")) }, session)
                }
            }
        val existingMods = modifiers.findAll().map { it._id }.toSet()
        catalog.mods.keys.sorted().filter { PoeCatalog.stableId("modifier:$it") !in existingMods }.chunked(100).forEach { batch ->
            transactionExecute("poe.seed.modifiers") { session -> modifiers.insertMany(batch.map(catalog::definition), session) }
        }
    }
}
