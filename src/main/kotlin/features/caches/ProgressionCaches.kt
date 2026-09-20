package features.caches

import features.logic.progression.CharacterClass
import features.logic.progression.CharacterClassRepository
import features.logic.progression.ExperienceLevel
import features.logic.progression.ExperienceLevelRepository

class CharacterClassCache(
    repository: CharacterClassRepository
) : MongoCache<CharacterClass, CharacterClassRepository>(repository) {

    fun findByCode(code: String): CharacterClass? = getCache().find { it.code == code }
}

class ExperienceLevelCache(
    repository: ExperienceLevelRepository
) : MongoCache<ExperienceLevel, ExperienceLevelRepository>(repository) {

    /**
     * Таблица уровней по возрастанию.
     */
    fun ordered(): List<ExperienceLevel> = getCache().sortedBy { it.level }

    /**
     * Уровень, достигнутый указанным опытом. Минимум первый.
     */
    fun levelOf(experience: Double): Int =
        ordered().lastOrNull { it.experience <= experience }?.level ?: 1

    /**
     * Сколько очков дерева накоплено к указанному уровню.
     */
    fun skillPointsUpTo(level: Int): Int =
        ordered().filter { it.level <= level }.sumOf { it.skillPoints }

    /**
     * Опыт, нужный для следующего уровня. null - уровень последний в таблице.
     */
    fun nextLevelExperience(level: Int): Double? =
        ordered().firstOrNull { it.level > level }?.experience
}
