package features.logic.atlas

import base.exception.model.CharacterExceptions
import com.sperance.exileforge.rules.content.AtlasAllocation
import com.sperance.exileforge.rules.content.AtlasBonuses
import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Атлас героя: взятые узлы (корень первым), достижения, очки и свободные очки. */
@Serializable
data class AtlasState(val allocated: List<String>, val earned: List<String>, val points: Int, val available: Int)

/** Пассивное дерево атласа: взятие, откат за золото и сброс; форма дерева и цены - из контента. */
class AtlasService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    fun bonuses(hero: Hero): AtlasBonuses = AtlasBonuses.of(index.atlasGraph, hero.atlas, index::modifier)

    fun state(hero: Hero): AtlasState {
        val tree = index.atlas
        return AtlasState(listOf(index.atlasGraph.start) + hero.atlas, hero.earned.toList(),
            AtlasPoints.total(tree.points, hero.earned, tree.cap), AtlasPoints.available(tree.points, hero.earned, hero.atlas, tree.cap))
    }

    suspend fun state(heroId: String): AtlasState = state(heroes.requireHero(heroId, "atlasState"))

    suspend fun allocate(heroId: String, nodeCode: String): AtlasState {
        val method = "allocateAtlas"
        val hero = heroes.requireHero(heroId, method)
        AtlasAllocation.requireAllocatable(index.atlasGraph, nodeCode, hero.atlas, state(hero).available)
        hero.atlas += nodeCode
        return state(heroes.save(hero, method))
    }

    suspend fun refund(heroId: String, nodeCode: String): AtlasState {
        val method = "refundAtlas"
        val hero = heroes.requireHero(heroId, method)
        AtlasAllocation.requireRefundable(index.atlasGraph, nodeCode, hero.atlas)
        charge(hero, index.atlas.respec.price(hero.level, 1), method)
        hero.atlas.remove(nodeCode)
        return state(heroes.save(hero, method))
    }

    /** Полный сброс стоит столько же, сколько поузловой откат всего взятого. */
    suspend fun reset(heroId: String): AtlasState {
        val method = "resetAtlas"
        val hero = heroes.requireHero(heroId, method)
        if (hero.atlas.isEmpty()) return state(hero)
        charge(hero, index.atlas.respec.price(hero.level, hero.atlas.size), method)
        hero.atlas.clear()
        return state(heroes.save(hero, method))
    }

    private fun charge(hero: Hero, price: Long, method: String) {
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.money -= price
    }
}
