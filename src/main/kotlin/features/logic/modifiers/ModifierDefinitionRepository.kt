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
import kotlinx.serialization.json.*
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
        val previous = latest(definition.id)
        require(previous == null || (previous.poe == null) == (definition.poe == null)) { "Cannot switch a modifier ID between PoE and custom definitions" }
        require((previous?.revision ?: 0) == expectedRevision) { "Definition changed; reload its latest revision" }
        definition.tiers.forEach { tier -> tier.values.forEach { require(it.min.isFinite() && it.max.isFinite() && it.min <= it.max) } }
        definition.poe?.let { raw ->
            require(raw.string("domain").isNotBlank() && raw.string("generation_type").isNotBlank())
            fun number(obj: JsonObject, key: String): Int {
                val value = obj[key] as? JsonPrimitive
                require(value != null && !value.isString && value.intOrNull != null) { "Expected integer: $key" }
                return value.int
            }
            val stats = raw["stats"] as? JsonArray ?: throw IllegalArgumentException("Expected stats array")
            require(stats.size <= 256)
            stats.forEach { value ->
                val stat = value as? JsonObject ?: throw IllegalArgumentException("Expected stat object")
                require(stat.string("id").isNotBlank() && number(stat, "min") <= number(stat, "max"))
            }
            require(number(raw, "required_level") >= 0)
            listOf("spawn_weights", "generation_weights").forEach { key ->
                raw[key]?.let { value ->
                    require(value is JsonArray) { "Expected weight array" }
                    value.forEach { row ->
                        val weight = row as? JsonObject ?: throw IllegalArgumentException("Expected weight object")
                        require(weight.string("tag").isNotBlank() && number(weight, "weight") >= 0)
                    }
                }
            }
            listOf("groups", "adds_tags", "implicit_tags").forEach { key ->
                raw[key]?.let { value ->
                    require(value is JsonArray && value.all { it is JsonPrimitive && it.isString && it.content.isNotBlank() }) { "Expected string array: $key" }
                }
            }
        }
        val normalized = definition.poe?.let { raw ->
            PoeCatalog.definitionFromRaw(definition.id, raw).copy(name = definition.name, enabled = definition.enabled)
        } ?: definition
        val revision = expectedRevision + 1
        val saved = normalized.copy(revision = revision, _id = PoeCatalog.stableId("modifier:${definition.id}:$revision"))
        collection.insertOne(saved)
        return saved
    }
}
