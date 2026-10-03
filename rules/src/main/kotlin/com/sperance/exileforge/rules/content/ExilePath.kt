package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/**
 * Путь изгнанника (1.74.0): первый час игры - шесть шагов по очереди, у каждого проверка по состоянию героя и награда.
 * Проверка чистая и общая: клиент подсвечивает цель и кнопку «Забрать», сервер по ней же платит. Подписи -
 * `path.<code>.title/text` в словаре клиента.
 */
@Serializable
enum class PathCheck { ZONE, EQUIP, TREE, SKILL, BOSS, ORB }

/** Награда шага: золото, стопки в сумку (сферы, сундук), вещь шаблона [item] редкости [rarity], либо редкая вещь по уровню героя. */
@Serializable
data class PathReward(
    val gold: Long = 0,
    val bag: Map<String, Long> = emptyMap(),
    val item: String = "",
    val rarity: Rarity = Rarity.MAGIC,
    val rare: Boolean = false,
)

@Serializable
data class PathStep(val code: String, val check: PathCheck, val reward: PathReward = PathReward())

@Serializable
data class PathRules(val steps: List<PathStep> = emptyList()) {
    fun step(index: Int): PathStep? = steps.getOrNull(index)
}

/**
 * Сделанное героем так, как его спрашивает путь: пройденные зоны, надевал ли он вещь сам, взятые узлы дерева
 * (узел класса не в счёт), умения, убитые боссы, потраченные сферы.
 */
data class PathFacts(
    val zones: Long,
    val equipped: Boolean,
    val treeNodes: Int,
    val skills: HeroSkills,
    val bosses: Long,
    val orbsUsed: Long,
) {
    fun done(check: PathCheck): Boolean = when (check) {
        PathCheck.ZONE -> zones >= 1

        PathCheck.EQUIP -> equipped

        PathCheck.TREE -> treeNodes >= 2

        // Второе умение в книге или первое поднятое - Гримуар открыт со 2 уровня.
        PathCheck.SKILL -> skills.active.count { it != null } >= 2 || skills.learned.values.sum() >= 3

        PathCheck.BOSS -> bosses >= 1

        PathCheck.ORB -> orbsUsed >= 1
    }
}
