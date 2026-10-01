package base.cache

/**
 * Ограниченный кеш в памяти: при переполнении уходит самая давно прочитанная запись, а запись старше [ttlMs]
 * считается отсутствующей. Заменяет сброс карты целиком по размеру - тот под нагрузкой разом отправлял
 * в базу все повторные чтения. Потокобезопасен: карта в порядке доступа под одним замком, операции короткие.
 */
class BoundedCache<K : Any, V>(private val maxSize: Int, private val ttlMs: Long = Long.MAX_VALUE) {
    private class Entry<V>(val value: V, val at: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>): Boolean = size > maxSize
    }

    /** Живое значение по [key]; просроченное удаляется. Отсутствие и сохранённый `null` не различаются - см. [lookup]. */
    operator fun get(key: K): V? = lookup(key)?.value

    /** Запись по [key], если жива: отличает сохранённый `null` от промаха. */
    fun lookup(key: K, now: Long = System.currentTimeMillis()): Lookup<V>? = synchronized(map) {
        val entry = map[key] ?: return null
        if (now - entry.at >= ttlMs) { map.remove(key); return null }
        Lookup(entry.value)
    }

    fun put(key: K, value: V, now: Long = System.currentTimeMillis()) { synchronized(map) { map[key] = Entry(value, now) } }

    fun remove(key: K) { synchronized(map) { map.remove(key) } }

    fun removeIf(predicate: (V) -> Boolean) = synchronized(map) { map.values.removeIf { predicate(it.value) } }

    fun clear() = synchronized(map) { map.clear() }

    val size: Int get() = synchronized(map) { map.size }

    @JvmInline value class Lookup<V>(val value: V)
}
