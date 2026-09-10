package features.poe

import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One immutable snapshot per operation. No per-affix database calls or JSON fallback.
 * Local publication invalidates immediately; other processes refresh within five seconds.
 * A referenced revision missing from a cached snapshot forces a refresh immediately. */
class MongoModifierCatalog(private val repository: ModifierDefinitionRepository,
    private val bases: Map<String, kotlinx.serialization.json.JsonObject> = PoeCatalog.bundled.bases,
    private val checkIntervalNanos: Long = 5_000_000_000L) {
    data class Snapshot(val catalog: PoeCatalog, val definitions: Map<ModifierRef, ModifierDefinition>) {
        fun resolve(ref: ModifierRef) = requireNotNull(definitions[ref]) { "Unknown modifier reference: $ref" }
    }
    init { require(checkIntervalNanos >= 0) }
    private val lock = Mutex()
    private var loadedGeneration = -1L
    @Volatile private var cached: Snapshot? = null
    @Volatile private var loadedAt = 0L
    suspend fun invalidate() = lock.withLock { loadedAt = 0L }
    suspend fun snapshot(required: Collection<ModifierRef> = emptyList()): Snapshot = lock.withLock {
        val now = System.nanoTime()
        cached?.let { current ->
            if (loadedAt != 0L && now - loadedAt < checkIntervalNanos && required.all { it in current.definitions }) return@withLock current
        }
        val generation = repository.generation()
        cached?.let { current ->
            if (generation == loadedGeneration && required.all { it in current.definitions }) {
                loadedAt = System.nanoTime()
                return@withLock current
            }
        }
        val definitions = repository.findAll()
        require(definitions.isNotEmpty()) { "Modifier collection is empty; seed MongoDB before serving requests" }
        val byRef = definitions.associateBy { ModifierRef(it.id, it.revision) }
        require(byRef.size == definitions.size) { "Duplicate modifier revisions" }
        val poeDefinitions = definitions.filter { it.poe != null }
        val heads = poeDefinitions.groupBy { it.id }.mapValues { (_, values) -> values.maxBy { it.revision } }
        val catalog = PoeCatalog(bases, heads.mapValues { it.value.poe!! },
            poeDefinitions.associate { (it.id to it.revision) to it.poe!! },
            heads.mapValues { it.value.revision }, heads.filterValues { !it.enabled }.keys)
        val result = Snapshot(catalog, byRef)
        required.forEach(result::resolve)
        cached = result
        loadedGeneration = generation
        loadedAt = System.nanoTime()
        result
    }
}
