package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/**
 * Класс героя (`classes.json`): база на первом уровне, прирост за уровень, стартовый узел дерева,
 * закреплённые строки - конверсии атрибутов, общие для всех классов, - и [weapon], обычное оружие,
 * которое новый герой класса получает надетым (1.2.0), и [difficulty] - сложность освоения от 1 до 3 для экрана выбора.
 */
@Serializable
data class HeroClass(
    val code: String,
    val startNode: String,
    val base: Map<String, Double> = emptyMap(),
    val perLevel: Map<String, Double> = emptyMap(),
    val lines: List<Line> = emptyList(),
    val weapon: String = "",
    val difficulty: Int = 0,
) {
    fun baseOn(level: Int): Map<String, Double> {
        val steps = (level - 1).coerceAtLeast(0)
        val result = base.toMutableMap()
        perLevel.forEach { (stat, growth) -> result[stat] = (result[stat] ?: 0.0) + growth * steps }
        return result
    }
}

/**
 * Файл `classes.json`: классы, накопленный опыт каждого уровня ([experience], индекс - уровень минус
 * один), очки дерева за уровень ([pointsPerLevel], [bonusEvery] уровней - ещё одно).
 */
@Serializable
data class ClassesFile(
    val classes: List<HeroClass> = emptyList(),
    val experience: List<Long> = emptyList(),
    val pointsPerLevel: Int = 1,
    val bonusEvery: Int = 10,
    /** База, одинаковая у всех классов, и прирост за уровень; свои значения класса ложатся поверх. */
    val sharedBase: Map<String, Double> = emptyMap(),
    val sharedPerLevel: Map<String, Double> = emptyMap(),
    val sharedLines: List<Line> = emptyList(),
) {
    val maxLevel: Int get() = experience.size

    fun heroClass(code: String): HeroClass? = classes.firstOrNull { it.code == code }

    /** Класс с общей базой, приростом и строками, сложенными с его собственными. */
    fun resolved(code: String): HeroClass? = heroClass(code)?.let { own ->
        own.copy(base = sharedBase + own.base, perLevel = sharedPerLevel + own.perLevel, lines = sharedLines + own.lines)
    }

    /** Порог опыта уровня [level]; null за пределами таблицы. */
    fun threshold(level: Int): Double? = experience.getOrNull(level - 1)?.toDouble()
    fun nextThreshold(level: Int): Double? = experience.getOrNull(level)?.toDouble()

    /** Уровень по накопленному опыту. */
    fun levelOf(experienceTotal: Double): Int {
        var level = 1
        while (level < maxLevel && experienceTotal >= experience[level]) level++
        return level
    }

    fun pointsAt(level: Int): Int = if (level <= 1) 0 else pointsPerLevel + if (bonusEvery > 0 && level % bonusEvery == 0) 1 else 0
    fun pointsTotal(level: Int): Int = (2..level).sumOf(::pointsAt)

    fun validate(stats: StatRegistry, modifier: (String) -> ModifierDef?, tree: TreeGraph) {
        if (classes.isEmpty()) fail("classes: empty")
        if (classes.map { it.code }.toSet().size != classes.size) fail("classes: duplicate codes")
        if (experience.isEmpty() || experience.zipWithNext().any { (a, b) -> a >= b } || experience[0] != 0L) fail("classes: experience table")
        classes.forEach { heroClass ->
            val resolved = resolved(heroClass.code)!!
            (resolved.base.keys + resolved.perLevel.keys).forEach { if (it !in stats) fail("classes: stat $it of ${heroClass.code}") }
            resolved.lines.forEach { line ->
                val def = modifier(line.code) ?: fail("classes: modifier ${line.code} of ${heroClass.code}")
                if (line.values.size != def.effects.size) fail("classes: values of ${line.code}")
            }
            val start = tree.node(heroClass.startNode) ?: fail("classes: start node ${heroClass.startNode} of ${heroClass.code}")
            if (start.type != SkillNodeType.START) fail("classes: ${heroClass.startNode} is not a start")
        }
    }
}
