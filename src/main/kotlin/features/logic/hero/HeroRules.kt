package features.logic.hero

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.sheet.SheetResult
import features.data.hero.Hero

/** Лист героя правилами `rules`: класс на уровне, строки дерева, надетое и гнёзда - то же, что считает клиент. */
fun ContentIndex.sheetOf(hero: Hero): SheetResult =
    SheetCalculator(this).calculate(hero.level, heroClass(hero.heroClass), tree.lines(hero.tree), hero.equipped, hero.tree.mapTo(HashSet()) { it.code })

/** Награда ложится на героя: стопки в сумку до потолка, опыт с уровнем, золото, копии вещей и рецепт. */
object Rewards {
    fun grant(hero: Hero, reward: Reward, index: ContentIndex) {
        reward.items.forEach { (code, amount) -> if (index.item(code) != null) hero.earn(code, amount, index.rules.maxStack) }
        if (reward.experience > 0) addExperience(hero, reward.experience, index)
        hero.money += reward.gold
        hero.items += reward.equipment
        reward.recipe?.let { if (it !in hero.recipes) hero.recipes += it }
    }

    /** Уровень только растёт: потеря опыта не забирает вложенных очков дерева. */
    fun addExperience(hero: Hero, amount: Double, index: ContentIndex) {
        hero.experience += amount
        val reached = index.classes.levelOf(hero.experience)
        if (reached > hero.level) hero.level = reached
    }
}
