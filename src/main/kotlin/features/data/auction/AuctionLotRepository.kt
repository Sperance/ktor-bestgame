package features.data.auction

import base.exception.model.AuctionExceptions
import base.exception.model.CharacterExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import base.route.PagedMongoResponse
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.text.LocaleKey
import config.ContentStore
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.hero.Stash
import features.logic.locale.LocaleCache
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Аукцион игроков. Товар на время торгов лежит в лоте: выставить одну вещь дважды или надеть
 * выставленную нельзя. Оплата, передача товара и закрытие лота идут одной транзакцией.
 */
class AuctionLotRepository : BaseRepository<AuctionLot>(AuctionLot::class), KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val rules get() = index.rules.auction

    override val indexes = listOf(IndexSpec.on("status", "_id"), IndexSpec.on("sellerId", "status"), IndexSpec.on("itemCode"))

    suspend fun search(heroId: String, search: AuctionSearch, page: Int, size: Int): PagedMongoResponse<AuctionLot> {
        requireTrader(heroId, "search")
        return findPaged(search.toFilter(), page, size)
    }

    /** Поисковый текст - в коды предметов по словарю языка: названий в лотах нет, а поиск остаётся на сервере. */
    fun codesMatching(language: String, text: String): List<String> {
        val bundle = LocaleCache.bundle(language)
        return bundle.codesMatching(LocaleKey.EQUIPMENT, text) + bundle.codesMatching(LocaleKey.ITEM, text)
    }

    /** Открытые лоты удаляемого героя снимаются: купить их уже нельзя, отменить некому. */
    suspend fun deleteActiveBySeller(sellerId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", LotStatus.ACTIVE.name)))
    }

    suspend fun findBySeller(heroId: String): List<AuctionLot> {
        requireTrader(heroId, "findBySeller")
        return findByFilter(Filters.eq("sellerId", heroId))
    }

    /** Выставляет вещь: она уходит из документа героя в лот, надетую сначала снимают. */
    suspend fun sellEquipment(heroId: String, itemId: String, priceOrb: String, price: Long): AuctionLot {
        val method = "sellEquipment"
        val seller = requireTrader(heroId, method)
        requirePlace(seller, method)
        requireOrb(priceOrb, method)
        requirePrice(price, method)
        val item = seller.requireItem(itemId, method)
        if (item.equipped || item.socketed) throw AuctionExceptions.funExceptionItemEquipped(method, itemId)
        val template = index.template(item.template) ?: throw CharacterExceptions.funExceptionEquipmentNotFound(method, item.template)
        seller.items.remove(item)
        return transactionExecute("auction $method $itemId") { session ->
            heroes.update(seller, session)
            insert(AuctionLot.forEquipment(seller, item, template, priceOrb, price), session)
        }
    }

    /** Выставляет стопку, в том числе сферы: списывается из сумки продавца и живёт в лоте. */
    suspend fun sellItem(heroId: String, code: String, amount: Long, priceOrb: String, price: Long): AuctionLot {
        val method = "sellItem"
        val seller = requireTrader(heroId, method)
        requirePlace(seller, method)
        requireOrb(priceOrb, method)
        requirePrice(price, method)
        if (amount <= 0) throw AuctionExceptions.funExceptionAmount(method, amount.toString())
        index.item(code) ?: throw CharacterExceptions.funExceptionItemNotFound(method, code)
        seller.spend(code, amount, method)
        return transactionExecute("auction $method $code") { session ->
            heroes.update(seller, session)
            insert(AuctionLot.forItem(seller, code, amount, priceOrb, price), session)
        }
    }

    /** Покупка: сферы уходят продавцу, товар - покупателю; не хватило сфер - откат, товар остаётся на витрине. */
    suspend fun buy(heroId: String, lotId: String): AuctionLot {
        val method = "buy"
        val buyer = requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId == heroId) throw AuctionExceptions.funExceptionOwnLot(method, lotId)
        val seller = heroes.findById(lot.sellerId) ?: throw CharacterExceptions.funExceptionNotFound(method, lot.sellerId)
        buyer.spend(lot.priceOrb, lot.price, method)
        seller.earn(lot.priceOrb, lot.price, index.rules.maxStack)
        deliver(lot, buyer)
        buyer.count(Counter.AUCTION_BOUGHT)
        seller.count(Counter.AUCTION_SOLD)
        return transactionExecute("auction $method $lotId") { session ->
            heroes.update(buyer, session)
            heroes.update(seller, session)
            close(lot, LotStatus.SOLD, heroId, session)
        }
    }

    suspend fun cancel(heroId: String, lotId: String): AuctionLot {
        val method = "cancel"
        val seller = requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId != heroId) throw AuctionExceptions.funExceptionNotSeller(method, lotId)
        deliver(lot, seller)
        return transactionExecute("auction $method $lotId") { session ->
            heroes.update(seller, session)
            close(lot, LotStatus.CANCELLED, null, session)
        }
    }

    private fun deliver(lot: AuctionLot, owner: Hero) {
        when (lot.kind) {
            LotKind.EQUIPMENT -> Stash.receive(owner, (lot.equipment ?: throw AuctionExceptions.funExceptionLotBroken("deliver", lot._id)).copy(slot = null, socket = null), index)
            LotKind.ITEM -> owner.earn(lot.item, lot.amount, index.rules.maxStack)
        }
    }

    private suspend fun close(lot: AuctionLot, status: LotStatus, buyerId: String?, session: ClientSession): AuctionLot {
        lot.status = status
        lot.buyerId = buyerId
        lot.closedAt = LocalDateTime.now()
        lot.equipment = null
        update(lot, session)
        return lot
    }

    /** Сколько лотов героя на витрине и сколько мест у него всего. */
    suspend fun slots(heroId: String): AuctionSlots {
        val seller = requireTrader(heroId, "slots")
        return slotsOf(seller, active(seller._id))
    }

    /** Докупает одно место за золото; цена растёт с каждым купленным. */
    suspend fun buySlot(heroId: String): AuctionSlots {
        val method = "buySlot"
        val seller = requireTrader(heroId, method)
        if (rules.baseSlots + seller.auctionSlots >= rules.maxSlots) throw AuctionExceptions.funExceptionSlotsMax(method, rules.maxSlots.toString())
        val price = rules.slotPrice(seller.auctionSlots)
        if (seller.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        seller.pay(price)
        seller.auctionSlots += 1
        heroes.save(seller, method)
        return slotsOf(seller, active(seller._id), seller.money)
    }

    private fun slotsOf(seller: Hero, used: Int, money: Long = 0): AuctionSlots {
        val limit = rules.baseSlots + seller.auctionSlots
        return AuctionSlots(used, limit, rules.maxSlots, if (limit >= rules.maxSlots) 0 else rules.slotPrice(seller.auctionSlots), money)
    }

    private suspend fun active(sellerId: String): Int =
        count(Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", LotStatus.ACTIVE.name))).toInt()

    private suspend fun requirePlace(seller: Hero, method: String) {
        val limit = rules.baseSlots + seller.auctionSlots
        if (active(seller._id) >= limit) throw AuctionExceptions.funExceptionLotLimit(method, limit.toString())
    }

    private suspend fun requireTrader(heroId: String, method: String): Hero {
        val hero = heroes.requireHero(heroId, method)
        if (hero.level < rules.minLevel) throw AuctionExceptions.funExceptionLevel(method, "${hero.level}, need ${rules.minLevel}")
        return hero
    }

    private suspend fun requireOpenLot(lotId: String, method: String): AuctionLot {
        val lot = findById(lotId) ?: throw AuctionExceptions.funExceptionLotNotFound(method, lotId)
        if (!lot.isOnSale()) throw AuctionExceptions.funExceptionLotClosed(method, lotId)
        return lot
    }

    private fun requireOrb(code: String, method: String) {
        val item = index.item(code) ?: throw CharacterExceptions.funExceptionItemNotFound(method, code)
        if (item.category != Item.CURRENCY) throw AuctionExceptions.funExceptionPriceNotOrb(method, code)
    }

    private fun requirePrice(price: Long, method: String) {
        if (price <= 0) throw AuctionExceptions.funExceptionPrice(method, price.toString())
    }
}

/** Места под лоты героя: базовые сразу, по одному докупается за золото до потолка; [price] - цена следующего, 0 - больше не купить. */
@Serializable
data class AuctionSlots(val used: Int, val limit: Int, val max: Int, val price: Long, val money: Long = 0)
