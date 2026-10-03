package ru.descend.exileforge.features.logic.hero
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.PathReward
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.table.Tables
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.exileforge.base.exception.model.ProgressionExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository

/** Забранный шаг пути: его код и следующий шаг (за последним - число шагов, путь пройден). */
@Serializable
data class PathClaimed(val step: String, val next: Int)

/**
 * Путь изгнанника (1.74.0): шаги забираются строго по очереди и только выполненные - проверка общая с клиентом
 * ([com.sperance.exileforge.rules.content.PathFacts]); награда кладётся в сумку и тайник одной записью героя.
 */
class ExilePathService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    suspend fun claim(heroId: String): PathClaimed {
        val method = "pathClaim"
        val hero = heroes.requireHero(heroId, method)
        val step = index.rules.path.step(hero.pathStep)?.takeIf { hero.pathFacts().done(it.check) }
            ?: throw ProgressionExceptions.funExceptionPathStep(method, hero.pathStep.toString())
        pay(hero, step.reward, Dice.system())
        hero.pathStep++
        heroes.save(hero, method)
        return PathClaimed(step.code, hero.pathStep)
    }

    private fun pay(hero: Hero, reward: PathReward, dice: Dice) {
        if (reward.gold > 0) hero.gain(reward.gold)
        reward.bag.forEach { (code, amount) -> if (index.template(code) != null) hero.earn(code, amount) }
        val factory = ItemFactory(index)
        val level = index.rules.loot.itemLevel(hero.level)
        val template = when {
            reward.item.isNotBlank() -> index.template(reward.item)
            reward.rare -> Tables.draw(index.templatePoolUpTo(listOf(DROP), hero.level).filter { (t) -> t.rarity < Rarity.UNIQUE && !t.slot.isJewelLike }, dice)
            else -> null
        } ?: return
        val rarity = if (reward.rare) Rarity.RARE else reward.rarity
        Stash.receive(hero, factory.create(Hero.newItemId(), template, rarity, dice, level = level), index)
    }

    private companion object {
        const val DROP = "drop"
    }
}
