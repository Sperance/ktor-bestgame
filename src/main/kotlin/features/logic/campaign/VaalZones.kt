package features.logic.campaign

import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/** Один модификатор пула Ваал-зоны: описание карты по [modifier] и его вес. */
@Serializable
data class VaalMod(val modifier: String, val weight: Int = 100)

/**
 * Ваал-зона (с 0.57.0) заменила осквернённый портал: за ним своя мини-карта со стражем в конце и
 * от [mods] модификаторов, свёрнутых из [pool] - тех же описаний, что катятся на предметы-карты, но
 * лучшего тира уровня карты и в [power] раз сильнее. Платит зона риском модификаторов, умноженным на
 * [reward], и ещё [perMod] процентов за каждый модификатор к количеству и редкости.
 */
@Serializable
data class VaalRule(
    val mods: List<Int> = listOf(3, 8),
    val power: Double = 1.5,
    val reward: Double = 1.5,
    val perMod: Double = 4.0,
    val pool: List<VaalMod> = emptyList(),
)

/**
 * Зона, какой её видит герой перед входом: модификаторы в порядке ролла, их эффекты по статам и то,
 * сколько процентов она добавляет добыче всего, что убито внутри, и стражу. Катится один раз за
 * заход карты и хранится у героя до входа или отказа - перекатить её переоткрытием нельзя.
 */
@Serializable
data class VaalZone(
    val mapCode: String,
    val level: Int,
    val modifiers: List<Modifier>,
    val effects: Map<String, Double>,
    val quantity: Double,
    val rarity: Double,
    val experience: Double,
)

/** Правило Ваал-зон. Чистое: описания и [Random] приходят снаружи. */
object VaalZones {

    fun roll(rule: VaalRule, maps: MapRule, mapCode: String, level: Int, definition: (String) -> ModifierDefinition?, random: Random): VaalZone {
        val pool = rule.pool.mapNotNull { mod -> definition(mod.modifier)?.takeIf { it.tiers.isNotEmpty() }?.let { it to mod.weight } }.toMutableList()
        val (low, high) = rule.mods.getOrElse(0) { 3 } to rule.mods.getOrElse(1) { 8 }
        val count = random.nextInt(low, high + 1).coerceAtMost(pool.size)
        val modifiers = List(count) {
            val (picked, _) = draw(pool, random)
            pool.removeAll { it.first === picked }
            val tier = picked.tiers.filter { it.level <= level }.maxByOrNull { it.level } ?: picked.tiers.minBy { it.level }
            val progress = random.nextDouble()
            Modifier(picked.code, tier.values.map { (min, max) -> tenths((min + (max - min) * progress) * rule.power) }, picked.tiers.indexOf(tier) + 1)
        }
        val effects = mutableMapOf<String, Double>()
        modifiers.forEach { modifier ->
            definition(modifier.modifierCode)?.effects?.forEachIndexed { index, effect ->
                effects.merge((effect.stat as Enum<*>).name, modifier.values.getOrElse(index) { 0.0 }, Double::plus)
            }
        }
        val risk = CampaignMaps.risk(maps, effects) * rule.reward
        val bonus = tenths(risk + rule.perMod * modifiers.size)
        return VaalZone(mapCode, level, modifiers, effects, bonus, bonus, tenths(risk))
    }

    private fun draw(pool: List<Pair<ModifierDefinition, Int>>, random: Random): Pair<ModifierDefinition, Int> {
        var ticket = random.nextInt(pool.sumOf { it.second }.coerceAtLeast(1))
        return pool.firstOrNull { (_, weight) -> ticket -= weight; ticket < 0 } ?: pool.last()
    }

    private fun tenths(value: Double) = (value * 10).roundToInt() / 10.0
}
