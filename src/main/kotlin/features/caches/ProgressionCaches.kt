package features.caches

import features.logic.progression.CharacterClass
import features.logic.progression.CharacterClassRepository
import features.logic.progression.ExperienceLevel
import features.logic.progression.ExperienceLevelRepository

class CharacterClassCache(
    repository: CharacterClassRepository
) : MongoCache<CharacterClass, CharacterClassRepository>(repository) {
    private val byCode = uniqueIndex { it.code }

    fun findByCode(code: String): CharacterClass? = byCode.get()[code]
}

class ExperienceLevelCache(
    repository: ExperienceLevelRepository
) : MongoCache<ExperienceLevel, ExperienceLevelRepository>(repository) {

    /** Таблица по возрастанию и очки дерева, накопленные к каждой строке. */
    private class Table(levels: List<ExperienceLevel>) {
        val ordered: List<ExperienceLevel> = levels.sortedBy { it.level }
        val pointsUpTo: IntArray = IntArray(ordered.size).also { sums ->
            var total = 0
            ordered.forEachIndexed { index, level -> total += level.skillPoints; sums[index] = total }
        }
    }

    private val table = derived { items -> Table(items) }

    /**
     * Таблица уровней по возрастанию.
     */
    fun ordered(): List<ExperienceLevel> = table.get().ordered

    /**
     * Уровень, достигнутый указанным опытом. Минимум первый.
     */
    fun levelOf(experience: Double): Int {
        val ordered = table.get().ordered
        val reached = reachedCount(ordered) { it.experience <= experience }
        return if (reached == 0) 1 else ordered[reached - 1].level
    }

    /**
     * Сколько очков дерева накоплено к указанному уровню.
     */
    fun skillPointsUpTo(level: Int): Int {
        val current = table.get()
        val reached = reachedCount(current.ordered) { it.level <= level }
        return if (reached == 0) 0 else current.pointsUpTo[reached - 1]
    }

    /**
     * Опыт, нужный для следующего уровня. null - уровень последний в таблице.
     */
    fun nextLevelExperience(level: Int): Double? {
        val ordered = table.get().ordered
        val reached = reachedCount(ordered) { it.level <= level }
        return ordered.getOrNull(reached)?.experience
    }

    /** Сколько строк с начала таблицы удовлетворяют монотонному условию [reached]. */
    private inline fun reachedCount(ordered: List<ExperienceLevel>, reached: (ExperienceLevel) -> Boolean): Int {
        var low = 0
        var high = ordered.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (reached(ordered[middle])) low = middle + 1 else high = middle
        }
        return low
    }
}
