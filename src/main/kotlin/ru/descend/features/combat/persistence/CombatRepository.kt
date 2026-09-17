package ru.descend.features.combat.persistence

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import kotlinx.serialization.Serializable
import ru.descend.features.combat.domain.*
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.shared.model.StockEntity

@Serializable data class CombatWorldDocument(override var _id: String = "default", val catalog: CombatCatalog) : StockEntity
@Serializable data class BattleReceipt(override var _id: String, val payload: String, val result: BattleView) : StockEntity
class BattleReceiptRepository : BaseRepository<BattleReceipt>(BattleReceipt::class) {
    init { initialize() }
}
class CombatWorldRepository : BaseRepository<CombatWorldDocument>(CombatWorldDocument::class) {
    suspend fun catalog(): CombatCatalog {
        findById("default")?.let { return it.catalog.also(CombatWorld::validate) }
        // Atomic set-on-insert is safe during parallel startup and preserves administrator edits.
        collection.updateOne(Filters.eq("_id", "default"), Updates.setOnInsert("catalog", CombatWorld.initial), UpdateOptions().upsert(true))
        return requireNotNull(findById("default")).catalog.also(CombatWorld::validate)
    }
}
