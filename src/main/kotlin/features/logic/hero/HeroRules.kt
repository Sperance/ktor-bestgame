package features.logic.hero

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Stat
import com.sperance.exileforge.rules.content.TreePlan
import com.sperance.exileforge.rules.roll.Menagerie
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.sheet.SellPrice
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.sheet.SheetResult
import com.sperance.exileforge.rules.sheet.sourcedLines
import features.data.hero.Hero

/** Лист героя правилами `rules`: класс на уровне, строки дерева, питомцы, надетое и гнёзда - то же, что считает клиент. */
fun ContentIndex.sheetOf(hero: Hero): SheetResult =
    SheetCalculator(this).calculate(hero.level, heroClass(hero.heroClass), tree.sourcedLines(hero.tree) + Menagerie(this).helperSourced(hero.activePets()), hero.equipped,
        hero.tree.mapTo(HashSet()) { it.code })

/** Награда ложится на героя: стопки в сумку до потолка, опыт с уровнем, золото, рецепт и копии вещей - через тайник. */
object Rewards {
    fun grant(hero: Hero, reward: Reward, index: ContentIndex): Received {
        reward.items.forEach { (code, amount) ->
            val item = index.item(code) ?: return@forEach
            hero.earn(code, amount)
            hero.stats.add(Stat.FOUND, code, amount)
            when (item.category) {
                Item.CURRENCY -> hero.count(Counter.ORBS_FOUND, amount)
                Item.ESSENCE -> hero.count(Counter.ESSENCES_FOUND, amount)
            }
        }
        if (reward.experience > 0) {
            addExperience(hero, reward.experience, index)
            val pets = Menagerie(index)
            hero.activePets().forEach { hero.replacePet(pets.gain(it, reward.experience * index.pets.experienceShare)) }
        }
        hero.gain(reward.gold)
        reward.recipe?.let { if (it !in hero.recipes) hero.recipes += it }
        reward.equipment.forEach { item ->
            when (item.rarity) {
                Rarity.MAGIC -> hero.count(Counter.ITEMS_MAGIC)
                Rarity.RARE -> hero.count(Counter.ITEMS_RARE)
                Rarity.UNIQUE -> hero.count(Counter.UNIQUES)
                Rarity.MYTHICAL -> { hero.count(Counter.UNIQUES); hero.count(Counter.MYTHICS) }
                Rarity.COMMON -> Unit
            }
            if (item.mapZone.isNotEmpty()) hero.count(Counter.MAPS_FOUND)
        }
        return autoSell(hero, reward.equipment, index) + Stash.receive(hero, reward.equipment.filterNot { sold(hero, it, index) }, index)
    }

    /** Добыча, что фильтр героя продаёт сразу: золото в кошелёк, в тайник она не ложится. */
    private fun autoSell(hero: Hero, items: List<ItemInstance>, index: ContentIndex): Received {
        val sold = items.filter { sold(hero, it, index) }
        if (sold.isEmpty()) return Received()
        val sheet = index.sheetOf(hero).stats
        val gold = sold.sumOf { item -> index.template(item.template)?.let { SellPrice.of(index, it, item, sheet) } ?: 0L }
        hero.gain(gold)
        hero.count(Counter.ITEMS_SOLD, sold.size.toLong())
        return Received(sold = sold.size, gold = gold)
    }

    private fun sold(hero: Hero, item: ItemInstance, index: ContentIndex): Boolean =
        index.template(item.template)?.let { hero.autoSell.sells(it, item) } == true

    /** Уровень только растёт: потеря опыта не забирает вложенных очков дерева. */
    fun addExperience(hero: Hero, amount: Double, index: ContentIndex) {
        hero.experience += amount
        val reached = index.classes.levelOf(hero.experience)
        if (reached > hero.level) {
            hero.level = reached
            followPlan(hero, index)
        }
    }

    /** Берёт узлы плана дерева, на которые теперь хватает очков (1.45.0). */
    fun followPlan(hero: Hero, index: ContentIndex) {
        val start = index.heroClass(hero.heroClass)?.startNode ?: return
        val available = index.classes.pointsTotal(hero.level) - index.tree.spent(hero.tree)
        hero.tree += TreePlan.follow(index.tree, hero.plannedTree, hero.tree.map { it.code }, start, available)
    }
}
