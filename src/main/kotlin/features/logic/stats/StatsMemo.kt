package features.logic.stats

/**
 * Листы героев, посчитанные недавно (с 0.49.0).
 *
 * Лист - функция персонажа, его надетых вещей и справочников. Все три отвечают за себя
 * счётчиками: `version` персонажа, `inventoryRevision` (растёт на каждую запись в инвентарь)
 * и ревизии кешей. Совпали счётчики - лист тот же, и ни базу читать, ни считать не нужно.
 */
object StatsMemo {
    private const val CAPACITY = 4096

    private class Entry(val key: String, val stats: CharacterStats)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > CAPACITY
    }

    /** Лист по ключу [key] (счётчики персонажа и справочников), иначе [compute] и запомнить. */
    suspend fun of(characterId: String, key: String, compute: suspend () -> CharacterStats): CharacterStats {
        synchronized(entries) { entries[characterId] }?.takeIf { it.key == key }?.let { return it.stats }
        val stats = compute()
        synchronized(entries) { entries[characterId] = Entry(key, stats) }
        return stats
    }

    fun forget(characterId: String) = synchronized(entries) { entries.remove(characterId); Unit }
}
