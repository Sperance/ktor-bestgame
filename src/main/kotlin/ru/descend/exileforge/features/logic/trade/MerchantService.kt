package ru.descend.exileforge.features.logic.trade
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.sha256
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.sheet.SellPrice
import com.sperance.exileforge.rules.table.Tables
import kotlinx.serialization.Serializable
import ru.descend.exileforge.base.cache.BoundedCache
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.hero.Stash

/** Вещь на витрине: уже выроленная копия и цена в золоте. */
@Serializable
data class MerchantOffer(val id: String, val item: ItemInstance, val price: Long)

/**
 * Сфера на полке (1.13.0): цена следующей, сколько куплено за окно и сколько ещё осталось (1.22.0);
 * [UNKNOWN] - окно, выложенное до запаса, - досчитывается при чтении.
 */
@Serializable
data class MerchantOrb(val code: String, val price: Long, val bought: Int = 0, val left: Int = UNKNOWN) {
    companion object {
        const val UNKNOWN = -1
    }
}

/**
 * Витрина героя: что на ней, полка сфер и когда торговец выложит новую (мс эпохи). [level] (1.62.0) - уровень героя,
 * под который выложены вещи: витрина окна детерминирована героем, окном и уровнем и пишется только покупкой.
 */
@Serializable
data class MerchantStock(val refreshAt: Long = 0, val offers: List<MerchantOffer> = emptyList(), val orbs: List<MerchantOrb> = emptyList(), val level: Int = 0)

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
 * за золото - бездонный сток золота: каждая покупка дороже, пока окно не сменится.
 */
class MerchantService(
    private val heroes: HeroRepository,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index

    /** Витрины окон: один и тот же расклад не роллится заново на каждый снимок героя. */
    private val laid = BoundedCache<String, List<MerchantOffer>>(LAID_MAX)

    /** Витрина на сейчас - только чтение: GET торговца в базу не пишет. */
    suspend fun stock(heroId: String): MerchantStock = current(heroes.requireHero(heroId, "merchant"))

    /**
     * Витрина героя на [now] (1.62.0). Окна выровнены по эпохе, а расклад вещей - по семени героя, окна и уровня:
     * вычисляется одинаково при каждом чтении, поэтому хранить его незачем. Сохранённое состояние (что куплено,
     * счётчики сфер) действует, пока окно то же; новый уровень внутри окна выкладывает новые вещи, полка сфер остаётся.
     */
    fun current(hero: Hero, now: Long = System.currentTimeMillis()): MerchantStock {
        val windowMs = (index.rules.merchant.windowHours * 3_600_000).toLong()
        val window = now / windowMs
        val refreshAt = (window + 1) * windowMs
        val saved = hero.merchant?.takeIf { it.refreshAt == refreshAt }
        val orbs = saved?.orbs?.map { if (it.left == MerchantOrb.UNKNOWN) it.copy(left = left(it.code, it.bought)) else it } ?: shelf(0)
        val offers = saved?.takeIf { it.level == hero.level }?.offers ?: offers(hero._id, hero.level, window)
        return MerchantStock(refreshAt, offers, orbs, hero.level)
    }

    private fun offers(heroId: String, level: Int, window: Long): List<MerchantOffer> {
        val key = "$heroId:$window:$level"
        return laid.get(key) ?: lay(level, Dice(sha256Long(key)), key).also { laid.put(key, it) }
    }

    /** Расклад вещей уровня [level] на костях [dice]; id копий выводятся из [seedKey] - одинаковы при каждом раскладе. */
    private fun lay(level: Int, dice: Dice, seedKey: String): List<MerchantOffer> {
        val rules = index.rules.merchant
        val factory = ItemFactory(index)
        val itemLevel = index.rules.loot.itemLevel(level)
        val (flasks, gear) = index.forHero(index.templatePool(rules.tables), level).partition { it.value.slot.isFlask }
        val near = gear.filter { it.value.requiredLevel in (level - rules.levelSpread)..(level + rules.levelSpread) }
            .ifEmpty { gear.filter { it.value.requiredLevel <= level + rules.levelSpread } }
        var serial = 0
        fun nextId(): String = sha256("$seedKey:${serial++}").take(24)

        // Волшебная и редкая на витрине не бывает пустой: шаблон, чья таблица не дотягивает до дна редкости,
        // заменяется другим, а после нескольких неудач торговец выкладывает вещь обычной
        fun offer(from: List<com.sperance.exileforge.rules.table.Weighted<ItemTemplate>>): MerchantOffer {
            val rarity = Tables.value<Rarity>(index.tables, rules.rarities, dice) ?: Rarity.COMMON
            val id = nextId()
            val (template, item) = (1..OFFER_TRIES).asSequence().map {
                val template = Tables.draw(from, dice) ?: from.first().value
                template to factory.create(id, template, rarity, dice, level = itemLevel)
            }.firstOrNull { (template, item) -> factory.meetsFloor(template, item) }
                ?: (Tables.draw(from, dice) ?: from.first().value).let { it to factory.create(id, it, Rarity.COMMON, dice, level = itemLevel) }
            val price = SellPrice.of(index, template, item) * rules.markup
            return MerchantOffer(item.id, item.copy(resale = SellPrice.resaleCap(index, price)), price)
        }
        val offers = if (near.isEmpty()) emptyList() else List(dice.between(rules.minOffers, rules.maxOffers)) { offer(near) }
        val shelf = flasks.filter { it.value.requiredLevel <= level }
        val bottles = if (shelf.isEmpty()) emptyList() else List(dice.between(rules.flasks)) { offer(shelf) }
        return offers + bottles
    }

    private fun sha256Long(text: String): Long = java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())).long

    /** Покупка: золото уходит торговцу, копия - в тайник, строка - с витрины. */
    suspend fun buy(heroId: String, offerId: String): MerchantPurchase {
        val method = "merchantBuy"
        val hero = heroes.requireHero(heroId, method)
        val stock = current(hero)
        val offer = stock.offers.firstOrNull { it.id == offerId } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, offerId)
        val price = offer.price
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
        hero.merchant = stock.copy(offers = stock.offers - offer)
        // Копия: расклад окна живёт в кеше и не должен меняться вместе с вещью героя
        Stash.receive(hero, offer.item.copy(), index)
        heroes.save(hero, method)
        return MerchantPurchase(offer.item, hero.money)
    }

    /** Покупка сферы с полки: золото торговцу, сфера в сумку, следующая того же вида дороже до конца окна; кончился запас - отказ. */
    suspend fun buyOrb(heroId: String, code: String): MerchantOrbPurchase {
        val method = "merchantBuyOrb"
        val hero = heroes.requireHero(heroId, method)
        val stock = current(hero)
        val orb = stock.orbs.firstOrNull { it.code == code } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, code)
        if (orb.left <= 0) throw CharacterExceptions.funExceptionOrbSoldOut(method, code)
        val price = orb.price
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
        hero.earn(code, 1)
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

        /** Сколько раскладов окон помнится в памяти. */
        const val LAID_MAX = 2_000
    }
}
