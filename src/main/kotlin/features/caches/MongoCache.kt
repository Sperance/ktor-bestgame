package features.caches

import base.entity.StockEntity
import base.repository.BaseRepository
import base.repository.EntityCache
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.printLog
import java.util.concurrent.atomic.AtomicReference

/**
 * Кеш справочной коллекции (переписан в 0.49.0).
 *
 * Состояние - неизменяемый [Snapshot]: список, индекс по `_id` и номер ревизии. Любая правка
 * подменяет снимок целиком, поэтому читатели никогда не видят кеш наполовину обновлённым
 * и не ловят ConcurrentModificationException, а `findById` стоит O(1).
 *
 * Производные индексы (по коду, по слоту, граф дерева) наследники объявляют через [derived]:
 * они считаются один раз на ревизию и лежат рядом со снимком, пока тот жив.
 */
abstract class MongoCache<T : StockEntity, R : BaseRepository<T>>(val repository: R) : EntityCache<T> {

    protected class Snapshot<T : StockEntity>(val items: List<T>, val revision: Long) {
        /** Строится при первом чтении: серия записей подряд (сидер) не пересобирает его на каждой. */
        val byId: Map<String, T> by lazy(LazyThreadSafetyMode.PUBLICATION) { items.associateBy { it._id } }
    }

    private val snapshot = AtomicReference(Snapshot<T>(emptyList(), 0))

    /**
     * Номер снимка. Растёт при каждой записи, поэтому [features.logic.world.WorldBundle] и
     * производные индексы узнают, что справочник изменился, не сравнивая содержимое.
     */
    val revision: Long get() = snapshot.get().revision

    suspend fun initializeCache() = loadToCache(repository.findAll())

    suspend fun initializeCache(session: ClientSession) = loadToCache(repository.findAll(session))

    fun loadToCache(data: Collection<T>) {
        replace { data.toList() }
        printLog("[${javaClass.simpleName}] initialized cache size: ${data.size}")
    }

    /** Вставка идемпотентна: сидер пишет и перечитывает коллекцию в одной транзакции, и запись не должна лечь дважды. */
    override fun addItem(item: T) = updateItem(item)

    override fun removeItem(item: T) = replace { items -> items.filterNot { it._id == item._id } }

    /** Правка на месте: порядок не сдвигается, а новая запись просто добавляется. */
    override fun updateItem(item: T) = replace { items ->
        val at = items.indexOfFirst { it._id == item._id }
        if (at < 0) items + item else items.toMutableList().apply { set(at, item) }
    }

    fun findById(id: String): T? = snapshot.get().byId[id]

    /** Записи по списку id, в порядке переданных id; неизвестные пропускаются. */
    fun findAllById(ids: Collection<String>): List<T> {
        val byId = snapshot.get().byId
        return ids.mapNotNull { byId[it] }
    }

    /** Текущий снимок: только чтение, писать в него нельзя. */
    fun getCache(): List<T> = snapshot.get().items

    fun isEmpty() = snapshot.get().items.isEmpty()

    private fun replace(transform: (List<T>) -> List<T>) {
        snapshot.updateAndGet { current -> Snapshot(transform(current.items), current.revision + 1) }
    }

    /** Индекс «ключ -> запись» ([key] уникален в справочнике), например по коду. */
    protected fun <K> uniqueIndex(key: (T) -> K): Derived<Map<K, T>> = derived { items -> items.associateBy(key) }

    /** Индекс «ключ -> записи» в порядке кеша, например по слоту или категории. */
    protected fun <K> groupIndex(key: (T) -> K): Derived<Map<K, List<T>>> = derived { items -> items.groupBy(key) }

    /**
     * Производный индекс: [build] запускается при первом чтении на новой ревизии,
     * дальше отдаётся готовое, пока снимок не сменится.
     */
    protected fun <V> derived(build: (List<T>) -> V): Derived<V> = Derived(build)

    protected inner class Derived<V>(private val build: (List<T>) -> V) {
        private val built = AtomicReference<Pair<Long, V>?>(null)

        fun get(): V {
            val current = snapshot.get()
            built.get()?.takeIf { it.first == current.revision }?.let { return it.second }
            val value = build(current.items)
            built.set(current.revision to value)
            return value
        }
    }
}
