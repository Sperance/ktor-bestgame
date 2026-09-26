package features.logic.campaign

import features.logic.atlas.AtlasBonuses
import features.logic.essences.CrystalRule
import features.logic.essences.EssenceContent
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.random.Random

/**
 * Кристалл эссенций в зоне (с 0.69.0): внутри [essences] - коды предметов, стережёт их [guardian] -
 * монстр зоны, что встанет редким с модификаторами своих эссенций. Сфера Ваал проходит по кристаллу
 * один раз ([vaal]) и может сделать стража сильнее ([stronger]).
 */
@Serializable
data class Crystal(val essences: List<String>, val guardian: String, val stronger: Boolean = false, val vaal: Boolean = false)

/** Окно кристаллов одной зоны у одного героя: как у сундуков, до [refreshAt] стоят те, что остались. */
@Serializable
data class CrystalWindow(val refreshAt: Long = 0, val crystals: List<Crystal> = emptyList())

/** Кристаллы зоны для клиента: что стоит и когда окно бросится заново. */
@Serializable
data class CrystalState(val crystals: List<Crystal>, val refreshAt: Long)

/** Чем кончилась сфера Ваал на кристалле: исход и кристаллы зоны после неё. */
@Serializable
data class CrystalVaal(val outcome: String, val crystal: Crystal, val state: CrystalState)

/**
 * Кристаллы эссенций - правило сервера, как сундуки. Функции чистые: время и [Random] приходят снаружи.
 */
object EssenceCrystals {

    /**
     * Окно на момент [now]: живое остаётся как есть, истёкшее бросается заново - от `count[0]` до
     * `count[1]` кристаллов, атлас прибавляет шанс ещё одного, плоские и процент сверху.
     */
    fun window(current: CrystalWindow?, now: Long, rule: CrystalRule, level: Int, monsters: List<String>, random: Random, atlas: AtlasBonuses): CrystalWindow {
        if (current != null && now < current.refreshAt) return current
        val base = random.nextInt(rule.count[0], rule.count[1] + 1) + (if (random.nextDouble() * 100 < atlas.crystalChance) 1 else 0) + atlas.crystals
        val scaled = base * (1 + atlas.crystalsMore / 100)
        val count = floor(scaled).toInt() + if (random.nextDouble() < scaled - floor(scaled)) 1 else 0
        return CrystalWindow(now + (rule.refreshHours * 3_600_000).toLong(), List(count.coerceAtLeast(0)) { roll(rule, level, monsters, random, atlas) })
    }

    /**
     * Один кристалл: эссенций от `essences[0]` до `essences[1]` (атлас - шанс ещё одной), каждая - ступени
     * зоны, с шансом [CrystalRule.lowerChance] на ступень ниже и с шансом атласа - на ступень выше.
     */
    fun roll(rule: CrystalRule, level: Int, monsters: List<String>, random: Random, atlas: AtlasBonuses): Crystal {
        val book = EssenceContent.book
        val zone = EssenceContent.tierOf(level)
        val count = random.nextInt(rule.essences[0], rule.essences[1] + 1) + if (random.nextDouble() * 100 < atlas.crystalEssences) 1 else 0
        val essences = List(count) {
            val lower = if (random.nextDouble() < rule.lowerChance) 1 else 0
            val higher = if (random.nextDouble() * 100 < atlas.crystalTier) 1 else 0
            val tier = (zone - lower + higher).coerceIn(1, book.tiers.size)
            EssenceContent.code(book.kinds.random(random).code, tier, false)
        }
        return Crystal(essences, monsters.random(random))
    }

    /**
     * Сфера Ваал на кристалле: по весам [CrystalRule.vaal] - все эссенции на ступень выше, одна
     * становится особой, или страж сильнее, а эссенции прежние. Кристалл после неё помечен.
     */
    fun vaal(crystal: Crystal, rule: CrystalRule, random: Random): Pair<String, Crystal> {
        val book = EssenceContent.book
        var point = random.nextInt(rule.vaal.values.sum())
        val outcome = rule.vaal.entries.first { (_, weight) -> point -= weight; point < 0 }.key
        val changed = when (outcome) {
            EssenceContent.VAAL_UPGRADE -> crystal.copy(essences = crystal.essences.map { code ->
                val essence = EssenceContent.essences[code]
                if (essence == null || essence.special) code else EssenceContent.code(essence.kind.code, (essence.tier + 1).coerceAtMost(book.tiers.size), false)
            })
            EssenceContent.VAAL_SPECIAL -> crystal.copy(essences = crystal.essences.toMutableList().also {
                it[random.nextInt(it.size)] = EssenceContent.code(book.specials.random(random).code, 0, true)
            })
            else -> crystal.copy(stronger = true)
        }
        return outcome to changed.copy(vaal = true)
    }
}
