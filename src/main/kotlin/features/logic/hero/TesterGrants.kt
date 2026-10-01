package features.logic.hero

import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.MAP_TEMPLATE
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ProfessionProgress
import com.sperance.exileforge.rules.table.Tables
import features.data.hero.Hero

/** Что сбрасывает окно тестирования (1.69.0). */
enum class TesterReset { TREE, ATLAS, BAG, STASH, CAMPAIGN }

/**
 * Выдачи окна тестирования (1.69.0): тестировщик и администратор получают своему герою всё, что игрок добывает игрой, -
 * без ограничений экономики. Каждая выдача меняет только документ героя; сохраняет вызывающий.
 */
class TesterGrants(private val index: ContentIndex) {
    private val factory = ItemFactory(index)

    fun gold(hero: Hero, amount: Long) { hero.money += amount.coerceIn(0, MAX_GOLD) }

    /** Уровень [level] ровно: опыт - порог уровня, план дерева берёт то, на что теперь хватает очков. */
    fun level(hero: Hero, level: Int) {
        val target = level.coerceIn(1, index.classes.maxLevel)
        hero.level = target
        hero.experience = index.classes.threshold(target) ?: hero.experience
        Rewards.followPlan(hero, index)
    }

    fun skillPoints(hero: Hero, amount: Int) {
        hero.bonusPoints = (hero.bonusPoints + amount).coerceIn(0, MAX_POINTS)
        Rewards.followPlan(hero, index)
    }

    /** Все очки атласа: каждое достижение каждой зоны засчитано. */
    fun atlasPoints(hero: Hero) {
        index.campaign.zones.forEach { zone -> AtlasPoints.KINDS.forEach { kind -> AtlasPoints.earn(hero.earned, kind, zone.code) } }
    }

    /** Все зоны кампании открыты и пройдены. */
    fun zones(hero: Hero) {
        index.campaign.zones.forEach { if (it.code !in hero.campaign.cleared) hero.campaign.cleared += it.code }
    }

    /** Вещь шаблона [code] редкости [rarity] (по умолчанию - шаблона) на уровне героя. */
    fun equipment(hero: Hero, code: String, rarity: Rarity?): Boolean {
        val template = index.template(code) ?: return false
        Stash.receive(hero, factory.create(Hero.newItemId(), template, rarity ?: template.rarity, Dice.system(), level = itemLevel(hero)), index)
        return true
    }

    /** [count] редких вещей случайных баз до уровня героя. */
    fun rares(hero: Hero, count: Int) {
        val bases = index.templatePoolUpTo(listOf(DROP), hero.level).filter { (template) -> template.rarity < Rarity.UNIQUE && !template.slot.isJewelLike }
        val dice = Dice.system()
        repeat(count.coerceIn(1, MAX_BATCH)) {
            val template = Tables.draw(bases, dice) ?: return
            Stash.receive(hero, factory.create(Hero.newItemId(), template, Rarity.RARE, dice, level = itemLevel(hero)), index)
        }
    }

    /** Карта зоны [zone] редкости [rarity]. */
    fun map(hero: Hero, zone: String, rarity: Rarity): Boolean {
        val target = index.zone(zone) ?: return false
        val template = index.template(MAP_TEMPLATE) ?: return false
        val map = factory.create(Hero.newItemId(), template, rarity, Dice.system(), level = target.level).also { it.mapZone = target.code }
        Stash.receive(hero, map, index)
        return true
    }

    /** Профессия [code] на уровне [level]; пустой код - все профессии. */
    fun profession(hero: Hero, code: String, level: Int) {
        val max = index.professions.rules.maxLevel
        val codes = if (code.isBlank()) index.professions.professions.map { it.code } else listOfNotNull(index.professions.profession(code)?.code)
        codes.forEach { hero.professions[it] = ProfessionProgress(level.coerceIn(1, max)) }
    }

    /** Все рецепты верстака известны. */
    fun recipes(hero: Hero) {
        index.bench.map { it.code }.filter { it !in hero.recipes }.forEach { hero.recipes += it }
    }

    fun reset(hero: Hero, what: TesterReset) {
        when (what) {
            TesterReset.TREE -> { hero.tree.clear(); hero.plannedTree.clear() }
            TesterReset.ATLAS -> hero.atlas.clear()
            TesterReset.BAG -> hero.bag.clear()
            TesterReset.STASH -> hero.items.removeAll { it.slot == null && it.socket == null }
            TesterReset.CAMPAIGN -> hero.campaign.cleared.clear()
        }
    }

    private fun itemLevel(hero: Hero): Int = index.rules.loot.itemLevel(hero.level)

    private companion object {
        const val DROP = "drop"
        const val MAX_GOLD = 1_000_000_000L
        const val MAX_POINTS = 1_000
        const val MAX_BATCH = 50
    }
}
