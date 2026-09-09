package features.logic.stats

interface StatResolver {

    fun resolve(
        stat: StatId,
        context: StatContext
    ): Double

    fun resolveAll(
        context: StatContext
    ): Map<StatId, Double>
}