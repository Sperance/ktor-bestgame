package features.logic.hero

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Menagerie
import com.sperance.exileforge.rules.run.Reward
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
        reward.items.forEach { (code, amount) -> if (index.item(code) != null) hero.earn(code, amount, index.rules.maxStack) }
        if (reward.experience > 0) {
            addExperience(hero, reward.experience, index)
            val pets = Menagerie(index)
            hero.activePets().forEach { hero.replacePet(pets.gain(it, reward.experience * index.pets.experienceShare)) }
        }
        hero.gain(reward.gold)
        reward.recipe?.let { if (it !in hero.recipes) hero.recipes += it }
        reward.equipment.forEach { item ->
            when (item.rarity) {
                Rarity.UNCOMMON -> hero.count(Counter.ITEMS_MAGIC)
                Rarity.RARE -> hero.count(Counter.ITEMS_RARE)
                Rarity.UNIQUE, Rarity.MYTHICAL -> hero.count(Counter.UNIQUES)
                Rarity.COMMON -> Unit
            }
        }
        return Stash.receive(hero, reward.equipment, index)
    }

    /** Уровень только растёт: потеря опыта не забирает вложенных очков дерева. */
    fun addExperience(hero: Hero, amount: Double, index: ContentIndex) {
        hero.experience += amount
        val reached = index.classes.levelOf(hero.experience)
        if (reached > hero.level) hero.level = reached
    }
}
