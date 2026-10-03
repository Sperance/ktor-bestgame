package ru.descend.exileforge.features.logic.atlas
import com.sperance.exileforge.rules.content.AtlasAllocation
import com.sperance.exileforge.rules.content.AtlasBonuses
import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Orb
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.exception.model.SkillTreeExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository

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
        return AtlasState(
            listOf(index.atlasGraph.start) + hero.atlas,
            hero.earned.toList(),
            AtlasPoints.total(tree.points, hero.earned, tree.cap),
            AtlasPoints.available(tree.points, hero.earned, hero.atlas, tree.cap),
        )
    }

    suspend fun state(heroId: String): AtlasState = state(heroes.requireHero(heroId, "atlasState"))

    suspend fun allocate(heroId: String, nodeCode: String): AtlasState {
        val method = "allocateAtlas"
        val hero = heroes.requireHero(heroId, method)
        AtlasAllocation.requireAllocatable(index.atlasGraph, nodeCode, hero.atlas, state(hero).available)
        hero.atlas += nodeCode
        return state(heroes.save(hero, method))
    }

    /** Откат узла: золотом по цене контента или, с [regret] (1.65.0), сферой сожаления - как узел дерева навыков. */
    suspend fun refund(heroId: String, nodeCode: String, regret: Boolean = false): AtlasState {
        val method = "refundAtlas"
        val hero = heroes.requireHero(heroId, method)
        AtlasAllocation.requireRefundable(index.atlasGraph, nodeCode, hero.atlas)
        charge(hero, 1, regret, method)
        hero.atlas.remove(nodeCode)
        return state(heroes.save(hero, method))
    }

    /** Полный сброс стоит столько же, сколько поузловой откат всего взятого: золотом или сферой сожаления за узел. */
    suspend fun reset(heroId: String, regret: Boolean = false): AtlasState {
        val method = "resetAtlas"
        val hero = heroes.requireHero(heroId, method)
        if (hero.atlas.isEmpty()) return state(hero)
        charge(hero, hero.atlas.size, regret, method)
        hero.atlas.clear()
        return state(heroes.save(hero, method))
    }

    private fun charge(hero: Hero, nodes: Int, regret: Boolean, method: String) {
        if (regret) {
            val owned = hero.bag[Orb.ORB_OF_REGRET.name] ?: 0L
            if (owned < nodes) throw SkillTreeExceptions.funExceptionNoRegret(method, "$nodes, have $owned")
            hero.spend(Orb.ORB_OF_REGRET.name, nodes.toLong(), method)
            return
        }
        val price = index.atlas.respec.price(hero.level, nodes)
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
    }
}
