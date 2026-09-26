package features.logic.trade

import base.exception.model.CharacterExceptions
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.sheet.SellPrice
import com.sperance.exileforge.rules.table.Tables
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Вещь на витрине: уже выроленная копия и цена в золоте. */
@Serializable
data class MerchantOffer(val id: String, val item: ItemInstance, val price: Long)

/** Витрина героя: что на ней и когда торговец выложит новую (мс эпохи). */
@Serializable
data class MerchantStock(val refreshAt: Long = 0, val offers: List<MerchantOffer> = emptyList())

/** Чем кончилась покупка: копия уже в тайнике, и сколько золота осталось. */
@Serializable
data class MerchantPurchase(val item: ItemInstance, val money: Long)

/**
 * Торговец: раз в окно правил выкладывает вещи уровня героя по весам редкости таблицы и полку фляг;
 * копия роллится при выкладке, поэтому игрок видит ровно то, что купит. Цена - то, что торговец дал
 * бы за такую вещь, умноженное на наценку: купить и сразу продать всегда в убыток.
 */
class MerchantService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    suspend fun stock(heroId: String): MerchantStock = restock(heroes.requireHero(heroId, "merchant"))

    /** Витрина на сейчас; сменившаяся записывается, и герой в памяти идёт в ногу с базой. */
    private suspend fun restock(hero: Hero): MerchantStock {
        val stock = lay(hero.merchant, hero.level, System.currentTimeMillis(), Dice.system())
        if (stock != hero.merchant) {
            hero.merchant = stock
            heroes.save(hero, "merchant")
        }
        return stock
    }

    fun lay(current: MerchantStock?, level: Int, now: Long, dice: Dice): MerchantStock {
        if (current != null && now < current.refreshAt) return current
        val rules = index.rules.merchant
        val factory = ItemFactory(index)
        val (flasks, gear) = index.templatePool(rules.tables).partition { it.value.slot.isFlask }
        val near = gear.filter { it.value.requiredLevel in (level - rules.levelSpread)..(level + rules.levelSpread) }
            .ifEmpty { gear.filter { it.value.requiredLevel <= level + rules.levelSpread } }
        fun offer(from: List<com.sperance.exileforge.rules.table.Weighted<ItemTemplate>>): MerchantOffer {
            val template = Tables.draw(from, dice) ?: from.first().value
            val rarity = Tables.value<Rarity>(index.tables, rules.rarities, dice) ?: Rarity.COMMON
            val item = factory.create(Hero.newItemId(), template, rarity, dice)
            return MerchantOffer(item.id, item, SellPrice.of(index, template, item.rarity, item.rolls.size, emptyMap()) * rules.markup)
        }
        val offers = if (near.isEmpty()) emptyList() else List(dice.between(rules.minOffers, rules.maxOffers)) { offer(near) }
        val shelf = flasks.filter { it.value.requiredLevel <= level }
        val bottles = if (shelf.isEmpty()) emptyList() else List(dice.between(rules.flasks)) { offer(shelf) }
        return MerchantStock(now + (rules.windowHours * 3_600_000).toLong(), offers + bottles)
    }

    /** Покупка: золото уходит торговцу, копия - в тайник, строка - с витрины. */
    suspend fun buy(heroId: String, offerId: String): MerchantPurchase {
        val method = "merchantBuy"
        val hero = heroes.requireHero(heroId, method)
        val stock = restock(hero)
        val offer = stock.offers.firstOrNull { it.id == offerId } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, offerId)
        if (hero.money < offer.price) throw CharacterExceptions.funExceptionGold(method, offer.price.toString())
        hero.money -= offer.price
        hero.merchant = stock.copy(offers = stock.offers - offer)
        hero.items += offer.item
        heroes.save(hero, method)
        return MerchantPurchase(offer.item, hero.money)
    }
}
