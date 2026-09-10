package features.logic.modifiers

import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.poe.PoeCatalog
import features.poe.objects
import features.poe.int
import features.poe.string
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Definitions are immutable revisions. Never update/delete a revision referenced by an item. */
class ModifierDefinitionRepository : BaseRepository<ModifierDefinition>(ModifierDefinition::class) {
    private val indexLock = Mutex()
    @Volatile private var ready = false
    suspend fun ensureRevisionIndex() = indexLock.withLock {
        if (!ready) {
            collection.updateMany(Filters.exists("revision", false), Updates.set("revision", 1))
            collection.createIndex(Indexes.compoundIndex(Indexes.ascending("id"), Indexes.ascending("revision")),
                IndexOptions().unique(true).name("modifier_id_revision"))
            ready = true
        }
    }
    suspend fun resolve(ref: ModifierRef, session: ClientSession? = null): ModifierDefinition? {
        val filter = Filters.and(Filters.eq("id", ref.definitionId), Filters.eq("revision", ref.revision))
        return if (session == null) collection.find(filter).firstOrNull() else collection.find(session, filter).firstOrNull()
    }
    suspend fun latest(id: String): ModifierDefinition? = collection.find(Filters.eq("id", id))
        .sort(com.mongodb.client.model.Sorts.descending("revision")).firstOrNull()

    /** expectedRevision=0 creates an ID. A unique index arbitrates concurrent publishers. */
    suspend fun publish(definition: ModifierDefinition, expectedRevision: Int): ModifierDefinition {
        ensureRevisionIndex()
        require(expectedRevision >= 0 && expectedRevision < Int.MAX_VALUE)
        require(definition.id.matches(Regex("[A-Za-z0-9_./:#-]{1,240}"))) { "Invalid modifier ID" }
        require(definition.name.isNotBlank())
        require((latest(definition.id)?.revision ?: 0) == expectedRevision) { "Definition changed; reload its latest revision" }
        definition.tiers.forEach { tier -> tier.values.forEach { require(it.min.isFinite() && it.max.isFinite() && it.min <= it.max) } }
        definition.poe?.let { raw ->
            require(raw.string("domain").isNotBlank() && raw.string("generation_type").isNotBlank())
            require(raw["stats"] is kotlinx.serialization.json.JsonArray)
            raw.objects("stats").forEach { stat ->
                require(stat.string("id").isNotBlank() && stat.int("min") <= stat.int("max"))
            }
            (raw.objects("spawn_weights") + raw.objects("generation_weights")).forEach { require(it.int("weight") >= 0) }
        }
        val revision = expectedRevision + 1
        val saved = definition.copy(revision = revision, _id = PoeCatalog.stableId("modifier:${definition.id}:$revision"))
        collection.insertOne(saved)
        return saved
    }
}
