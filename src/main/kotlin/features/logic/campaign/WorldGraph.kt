package features.logic.campaign

/**
 * Связи зон карты мира (с 0.67.0). Жетоны связаны от старта вверх по уровням: в зону ведут связи из
 * её [CampaignMapTemplate.from], и открыта она, когда пройдена хоть одна из них - пройдена значит
 * убит её босс. Стартовая зона, без связей, открыта всегда.
 */
class WorldGraph(zones: List<CampaignMapTemplate>) {

    private val codes: List<String> = zones.map { it.code }

    private val from: Map<String, List<String>> = zones.associate { it.code to it.from }

    private val to: Map<String, List<String>> = zones.flatMap { zone -> zone.from.map { it to zone.code } }
        .groupBy({ it.first }, { it.second })

    /** Зоны, в которые ведут связи из [code]. */
    fun next(code: String): List<String> = to[code].orEmpty()

    /** Открытые зоны в порядке файла: стартовая и все, у кого пройдена хоть одна зона из [from]. */
    fun unlocked(passed: Collection<String>): List<String> {
        val done = passed.toHashSet()
        return codes.filter { code -> from.getValue(code).let { it.isEmpty() || it.any(done::contains) } }
    }

    /** Зоны, до которых по связям не дойти от стартовых, - ошибка файла, а не тайник. */
    fun unreachable(): Set<String> {
        val seen = codes.filterTo(HashSet()) { from.getValue(it).isEmpty() }
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) next(queue.removeFirst()).forEach { if (seen.add(it)) queue.addLast(it) }
        return codes.toSet() - seen
    }
}
