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
import features.logic.hero.Stash
import features.logic.hero.guildBonus
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Вещь на витрине: уже выроленная копия и цена в золоте. */
@Serializable
data class MerchantOffer(val id: String, val item: ItemInstance, val price: Long)

/**
 * Сфера на полке (1.13.0): цена следующей, сколько куплено за окно и сколько ещё осталось (1.22.0);
 * [UNKNOWN] - окно, выложенное до запаса, - досчитывается при чтении.
 */
@Serializable
data class MerchantOrb(val code: String, val price: Long, val bought: Int = 0, val left: Int = UNKNOWN) {
    companion object { const val UNKNOWN = -1 }
}

/** Витрина героя: что на ней, полка сфер и когда торговец выложит новую (мс эпохи). */
@Serializable
data class MerchantStock(val refreshAt: Long = 0, val offers: List<MerchantOffer> = emptyList(), val orbs: List<MerchantOrb> = emptyList())

/** Чем кончилась покупка: копия уже в тайнике, и сколько золота осталось. */
@Serializable
data class MerchantPurchase(val item: ItemInstance, val money: Long)

/** Чем кончилась покупка сферы: она в сумке, сколько золота осталось и почём следующая. */
@Serializable
data class MerchantOrbPurchase(val code: String, val money: Long, val next: Long)

/**
 * Торговец: раз в окно правил выкладывает вещи уровня героя по весам редкости таблицы и полку фляг;
 * копия роллится при выкладке, поэтому игрок видит ровно то, что купит. Цена - то, что торговец дал
 * бы за такую вещь, умноженное на наценку: купить и сразу продать всегда в убыток. Полка низших сфер
 * за золото - бездонный сток золота: каждая покупка дороже, пока окно не сменится. Цены витрины - прейскурант:
 * скидка гильдии (покровитель торговли) списывается при покупке.
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
        if (current != null && now < current.refreshAt) return current.copy(orbs = current.orbs.map { if (it.left == MerchantOrb.UNKNOWN) it.copy(left = left(it.code, it.bought)) else it })
        val rules = index.rules.merchant
        val factory = ItemFactory(index)
        val (flasks, gear) = index.templatePool(rules.tables).partition { it.value.slot.isFlask }
        val near = gear.filter { it.value.requiredLevel in (level - rules.levelSpread)..(level + rules.levelSpread) }
            .ifEmpty { gear.filter { it.value.requiredLevel <= level + rules.levelSpread } }
        // Волшебная и редкая на витрине не бывает пустой: шаблон, чья таблица не дотягивает до дна редкости,
        // заменяется другим, а после нескольких неудач торговец выкладывает вещь обычной
        fun offer(from: List<com.sperance.exileforge.rules.table.Weighted<ItemTemplate>>): MerchantOffer {
            val rarity = Tables.value<Rarity>(index.tables, rules.rarities, dice) ?: Rarity.COMMON
            val (template, item) = (1..OFFER_TRIES).asSequence().map {
                val template = Tables.draw(from, dice) ?: from.first().value
                template to factory.create(Hero.newItemId(), template, rarity, dice)
            }.firstOrNull { (template, item) -> factory.meetsFloor(template, item) }
                ?: (Tables.draw(from, dice) ?: from.first().value).let { it to factory.create(Hero.newItemId(), it, Rarity.COMMON, dice) }
            return MerchantOffer(item.id, item, SellPrice.of(index, template, item) * rules.markup)
        }
        val offers = if (near.isEmpty()) emptyList() else List(dice.between(rules.minOffers, rules.maxOffers)) { offer(near) }
        val shelf = flasks.filter { it.value.requiredLevel <= level }
        val bottles = if (shelf.isEmpty()) emptyList() else List(dice.between(rules.flasks)) { offer(shelf) }
        return MerchantStock(now + (rules.windowHours * 3_600_000).toLong(), offers + bottles, shelf(0))
    }

    /** Покупка: золото уходит торговцу, копия - в тайник, строка - с витрины. */
    suspend fun buy(heroId: String, offerId: String): MerchantPurchase {
        val method = "merchantBuy"
        val hero = heroes.requireHero(heroId, method)
        val stock = restock(hero)
        val offer = stock.offers.firstOrNull { it.id == offerId } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, offerId)
        val price = index.guildBonus(hero).discounted(offer.price)
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
        hero.merchant = stock.copy(offers = stock.offers - offer)
        Stash.receive(hero, offer.item, index)
        heroes.save(hero, method)
        return MerchantPurchase(offer.item, hero.money)
    }

    /** Покупка сферы с полки: золото торговцу, сфера в сумку, следующая того же вида дороже до конца окна; кончился запас - отказ. */
    suspend fun buyOrb(heroId: String, code: String): MerchantOrbPurchase {
        val method = "merchantBuyOrb"
        val hero = heroes.requireHero(heroId, method)
        val stock = restock(hero)
        val orb = stock.orbs.firstOrNull { it.code == code } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, code)
        if (orb.left <= 0) throw CharacterExceptions.funExceptionOrbSoldOut(method, code)
        val price = index.guildBonus(hero).discounted(orb.price)
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
        hero.earn(code, 1, index.rules.maxStack)
        val next = orb.copy(price = orbPrice(code, orb.bought + 1), bought = orb.bought + 1, left = orb.left - 1)
        hero.merchant = stock.copy(orbs = stock.orbs.map { if (it.code == code) next else it })
        heroes.save(hero, method)
        return MerchantOrbPurchase(code, hero.money, next.price)
    }

    private fun shelf(bought: Int): List<MerchantOrb> = index.rules.merchant.orbs.codes.map { MerchantOrb(it, orbPrice(it, bought), bought, left(it, bought)) }

    private fun left(code: String, bought: Int): Int = (index.rules.merchant.orbs.stockOf(code) - bought).coerceAtLeast(0)

    private fun orbPrice(code: String, bought: Int): Long = index.rules.merchant.orbs.price(index.item(code)?.price ?: 0, bought)

    private companion object {
        /** Сколько шаблонов торговец перебирает, прежде чем выложить вещь обычной. */
        const val OFFER_TRIES = 8
    }
}
