package features.logic.tree

import base.exception.model.SkillTreeExceptions
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.SkillNodeType
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.content.TreeAllocation
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.sheet.StatContribution
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Дерево героя: очки на уровне, потраченные, свободные, взятые узлы и вклад дерева по характеристикам. */
@Serializable
data class TreeState(val total: Int, val spent: Int, val available: Int, val nodes: List<TakenNode>, val totals: List<StatContribution>)

/**
 * Дерево навыков: со стартового узла класса, только соседи взятого, откат за Сферу сожаления без
 * обрыва дерева, смена варианта атрибутного узла за Сферу хаоса. Правила - в `rules`.
 */
class TreeService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    fun state(hero: Hero): TreeState {
        val total = index.classes.pointsTotal(hero.level)
        val spent = index.tree.spent(hero.tree)
        val calculator = SheetCalculator(index)
        return TreeState(total, spent, total - spent, hero.tree.toList(), calculator.contributions(calculator.expand(index.tree.lines(hero.tree))))
    }

    suspend fun state(heroId: String): TreeState = state(heroes.requireHero(heroId, "treeState"))

    suspend fun allocate(heroId: String, nodeCode: String, choice: Int?): TreeState {
        val method = "allocate"
        val hero = heroes.requireHero(heroId, method)
        val node = index.tree.node(nodeCode) ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)
        val start = index.heroClass(hero.heroClass)?.startNode ?: throw SkillTreeExceptions.funExceptionNoStart(method, hero.heroClass)
        TreeAllocation.requireAllocatable(index.tree, node, hero.tree.map { it.code }, start, state(hero).available, choice)
        hero.tree += TakenNode(nodeCode, choice)
        return state(heroes.save(hero, method))
    }

    /**
     * Весь путь к узлу разом (1.37.0): кратчайший путь правил, за сумму цен; [choice] - вариант самой цели.
     * Каждый шаг проходит те же проверки, что и одиночное взятие; герой сохраняется один раз.
     */
    suspend fun allocatePath(heroId: String, nodeCode: String, choice: Int?): TreeState {
        val method = "allocatePath"
        val hero = heroes.requireHero(heroId, method)
        index.tree.node(nodeCode) ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)
        val start = index.heroClass(hero.heroClass)?.startNode ?: throw SkillTreeExceptions.funExceptionNoStart(method, hero.heroClass)
        val path = TreeAllocation.path(index.tree, hero.tree.map { it.code }, start, nodeCode)
            ?: throw SkillTreeExceptions.funExceptionNotAdjacent(method, nodeCode)
        var available = state(hero).available
        path.forEach { code ->
            val node = index.tree.node(code)!!
            val pick = choice.takeIf { code == nodeCode }
            TreeAllocation.requireAllocatable(index.tree, node, hero.tree.map { it.code }, start, available, pick)
            hero.tree += TakenNode(code, pick)
            available -= node.cost
        }
        return state(heroes.save(hero, method))
    }

    /** Другой вариант взятого атрибутного узла за Сферу хаоса; мастерство меняется только возвратом. */
    suspend fun rechoose(heroId: String, nodeCode: String, choice: Int): TreeState {
        val method = "rechoose"
        val hero = heroes.requireHero(heroId, method)
        val node = index.tree.node(nodeCode) ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)
        if (node.type == SkillNodeType.MASTERY || node.options.isEmpty()) throw SkillTreeExceptions.funExceptionNotRechoosable(method, nodeCode)
        if (choice !in node.options.indices) throw SkillTreeExceptions.funExceptionChoice(method, "$nodeCode: $choice of ${node.options.size}")
        val at = hero.tree.indexOfFirst { it.code == nodeCode }
        if (at < 0) throw SkillTreeExceptions.funExceptionNotTaken(method, nodeCode)
        if (hero.tree[at].choice == choice) throw SkillTreeExceptions.funExceptionChoice(method, "$nodeCode: $choice is already chosen")
        if ((hero.bag[Orb.CHAOS_ORB.name] ?: 0L) < 1) throw SkillTreeExceptions.funExceptionNoChaos(method, nodeCode)
        hero.spend(Orb.CHAOS_ORB.name, 1, method)
        hero.tree[at] = TakenNode(nodeCode, choice)
        return state(heroes.save(hero, method))
    }

    suspend fun refund(heroId: String, nodeCode: String): TreeState {
        val method = "refund"
        val hero = heroes.requireHero(heroId, method)
        val node = index.tree.node(nodeCode) ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)
        TreeAllocation.requireRefundable(index.tree, node, hero.tree.map { it.code })
        TreeAllocation.requireSocketEmpty(node, hero.sockets())
        spendRegret(hero, 1, method)
        hero.tree.removeAll { it.code == nodeCode }
        return state(heroes.save(hero, method))
    }

    /** Полный сброс: по сфере за каждый возвращаемый узел, герой остаётся на стартовом узле класса. */
    suspend fun reset(heroId: String): TreeState {
        val method = "reset"
        val hero = heroes.requireHero(heroId, method)
        val sockets = hero.sockets()
        hero.tree.mapNotNull { index.tree.node(it.code) }.forEach { TreeAllocation.requireSocketEmpty(it, sockets) }
        val returned = hero.tree.count { index.tree.node(it.code)?.type != SkillNodeType.START }
        if (returned > 0) spendRegret(hero, returned, method)
        val start = index.heroClass(hero.heroClass)?.startNode ?: throw SkillTreeExceptions.funExceptionNoStart(method, hero.heroClass)
        hero.tree = mutableListOf(TakenNode(start))
        return state(heroes.save(hero, method))
    }

    private fun spendRegret(hero: Hero, count: Int, method: String) {
        val owned = hero.bag[Orb.ORB_OF_REGRET.name] ?: 0L
        if (owned < count) throw SkillTreeExceptions.funExceptionNoRegret(method, "$count, have $owned")
        hero.spend(Orb.ORB_OF_REGRET.name, count.toLong(), method)
    }
}
