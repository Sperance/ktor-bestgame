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
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.text.LocaleKey
import config.ContentStore
import config.MongoFactory.transactionExecute
import extensions.now
import extensions.printLog
import kotlinx.coroutines.CancellationException
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
 *
 * С 1.30.0 лот стоит на витрине `auction.lotDays` дней: истёкший закрывается [LotStatus.EXPIRED] лениво - на
 * витрине, в своих лотах и на любом запросе продавца, - товар возвращается продавцу, сбор не возвращается.
 * Стопка на аукционе не сгорает: покупка, при которой стопка продавца или покупателя перелилась бы через
 * `maxStack`, отклоняется, ничего не списав. Вещь лота сверяется с контентом, когда лот показывают.
 */
class AuctionLotRepository : BaseRepository<AuctionLot>(AuctionLot::class), KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val rules get() = index.rules.auction

    override val indexes = listOf(IndexSpec.on("status", "_id"), IndexSpec.on("sellerId", "status"), IndexSpec.on("itemCode"), IndexSpec.on("status", "expiresAt"))

    suspend fun search(heroId: String, search: AuctionSearch, page: Int, size: Int): PagedMongoResponse<AuctionLot> {
        requireTrader(heroId, "search")
        expireDue(null)
        val found = findPaged(search.toFilter(), page, size)
        return found.copy(items = found.items.map { reconciled(it) })
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
        return findByFilter(Filters.eq("sellerId", heroId)).map { reconciled(it) }
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
        if (item.locked) throw AuctionExceptions.funExceptionItemLocked(method, itemId)
        val template = index.template(item.template) ?: throw CharacterExceptions.funExceptionEquipmentNotFound(method, item.template)
        seller.items.remove(item)
        return transactionExecute("auction $method $itemId") { session ->
            heroes.update(seller, session)
            insert(AuctionLot.forEquipment(seller, item, template, priceOrb, price, fee(priceOrb, price), expiry()), session)
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
            insert(AuctionLot.forItem(seller, code, amount, priceOrb, price, fee(priceOrb, price), expiry()), session)
        }
    }

    /**
     * Покупка: сферы уходят продавцу, товар - покупателю, сбор золотом сгорает; не хватило сфер или золота,
     * стопка продавца (`AU_015`) или покупателя (`AU_016`) перелилась бы через потолок - отказ, ничего не
     * списано, товар остаётся на витрине.
     */
    suspend fun buy(heroId: String, lotId: String): AuctionLot {
        val method = "buy"
        val buyer = requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId == heroId) throw AuctionExceptions.funExceptionOwnLot(method, lotId)
        val seller = heroes.findById(lot.sellerId) ?: throw CharacterExceptions.funExceptionNotFound(method, lot.sellerId)
        val fee = lot.fee
        if (buyer.money < fee) throw CharacterExceptions.funExceptionGold(method, fee.toString())
        val cap = index.rules.maxStack
        if (!fits(seller, lot.priceOrb, lot.price)) throw AuctionExceptions.funExceptionSellerStackFull(method, cap.toString())
        buyer.spend(lot.priceOrb, lot.price, method)
        if (lot.kind == LotKind.ITEM && !fits(buyer, lot.item, lot.amount)) throw AuctionExceptions.funExceptionStackFull(method, cap.toString())
        buyer.pay(fee)
        seller.earn(lot.priceOrb, lot.price, cap)
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
        if (lot.kind == LotKind.ITEM && !fits(seller, lot.item, lot.amount)) throw AuctionExceptions.funExceptionStackFull(method, index.rules.maxStack.toString())
        giveBack(lot, seller)
        return transactionExecute("auction $method $lotId") { session ->
            heroes.update(seller, session)
            close(lot, LotStatus.CANCELLED, null, session)
        }
    }

    private fun fee(priceOrb: String, price: Long): Long = index.rules.auction.fee(index.item(priceOrb)?.price ?: 0, price)

    private fun deliver(lot: AuctionLot, owner: Hero) {
        when (lot.kind) {
            LotKind.EQUIPMENT -> Stash.receive(owner, goods(lot), index)
            LotKind.ITEM -> owner.earn(lot.item, lot.amount, index.rules.maxStack)
        }
    }

    /** Товар обратно продавцу: вещь - в тайник или переполнение, не торговцу; стопку вызывающий уже проверил на место. */
    private fun giveBack(lot: AuctionLot, owner: Hero) {
        when (lot.kind) {
            LotKind.EQUIPMENT -> Stash.giveBack(owner, goods(lot), index)
            LotKind.ITEM -> owner.earn(lot.item, lot.amount, index.rules.maxStack)
        }
    }

    private fun goods(lot: AuctionLot) = (lot.equipment ?: throw AuctionExceptions.funExceptionLotBroken("deliver", lot._id)).copy(slot = null, socket = null)

    /** Влезет ли [amount] предмета [code] в сумку героя, не перелившись через потолок стопки. */
    private fun fits(hero: Hero, code: String, amount: Long): Boolean = (hero.bag[code] ?: 0L) <= index.rules.maxStack - amount

    private fun expiry(): Long = System.currentTimeMillis() + rules.lotMillis

    /**
     * Лот, каким его показывают: старому лоту без срока он выводится из даты выставления (1.30.2), вещь сверена
     * с контентом так же, как её сверит запись героя. Изменилось что-то - лот переписывается, иначе следующий
     * показ докатил бы вещь иначе; сбой записи показа не срывает, срок отдаётся клиенту в любом случае.
     */
    private suspend fun reconciled(lot: AuctionLot): AuctionLot {
        val dated = lot.assignDeadline(rules.lotMillis)
        if (reconcileItem(lot) || dated) quietly("reconcile ${lot._id}") { transactionExecute("auction reconcile ${lot._id}") { session -> update(lot, session) } }
        return lot
    }

    /** Сверяет вещь лота с контентом; true - вещь изменилась. */
    private fun reconcileItem(lot: AuctionLot): Boolean {
        val item = lot.equipment ?: return false
        val template = index.template(item.template) ?: return false
        if (!ItemFactory(index).reconcile(template, item, Dice.system())) return false
        lot.rarity = item.rarity
        return true
    }

    /**
     * Истёкшие лоты - продавца [sellerId] или, без него, все - закрываются: товар возвращается продавцу, сбор
     * сгорел при покупке и не возвращается. Старому лоту без срока он сперва выводится из даты выставления.
     * Стопка, которой продавцу некуда лечь, ждёт на витрине, пока место не появится: сгореть она не должна.
     */
    private suspend fun expireDue(sellerId: String?) {
        val now = System.currentTimeMillis()
        val scope = listOfNotNull(Filters.eq("status", LotStatus.ACTIVE.name), sellerId?.let { Filters.eq("sellerId", it) })
        findByFilter(Filters.and(scope + Filters.or(Filters.exists("expiresAt", false), Filters.eq("expiresAt", 0L)))).forEach { lot ->
            lot.assignDeadline(rules.lotMillis)
            quietly("deadline ${lot._id}") { transactionExecute("auction deadline ${lot._id}") { session -> update(lot, session) } }
        }
        findByFilter(Filters.and(scope + Filters.gt("expiresAt", 0L) + Filters.lte("expiresAt", now))).forEach { lot ->
            quietly("expire ${lot._id}") { expire(lot) }
        }
    }

    private suspend fun expire(lot: AuctionLot) {
        val seller = heroes.findById(lot.sellerId)
        if (seller != null) {
            if (lot.kind == LotKind.ITEM && !fits(seller, lot.item, lot.amount)) return
            giveBack(lot, seller)
        }
        transactionExecute("auction expire ${lot._id}") { session ->
            seller?.let { heroes.update(it, session) }
            close(lot, LotStatus.EXPIRED, null, session)
        }
    }

    /** Попутная работа (срок, сверка): гонка версий или сбой базы её откладывает до следующего запроса, но не срывает запрос. */
    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { printLog("[Auction] $what: ${e.message}") }
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

    /** Герой аукциона; сперва закрываются его истёкшие лоты - товар возвращается прежде, чем он что-то сделает. */
    private suspend fun requireTrader(heroId: String, method: String): Hero {
        expireDue(heroId)
        val hero = heroes.requireHero(heroId, method)
        if (hero.level < rules.minLevel) throw AuctionExceptions.funExceptionLevel(method, "${hero.level}, need ${rules.minLevel}")
        return hero
    }

    /** Лот на витрине; истёкший - закрыт (товар ушёл продавцу) и не продаётся: `AU_004`. */
    private suspend fun requireOpenLot(lotId: String, method: String): AuctionLot {
        val lot = findById(lotId) ?: throw AuctionExceptions.funExceptionLotNotFound(method, lotId)
        if (!lot.isOnSale()) {
            if (lot.status == LotStatus.ACTIVE) quietly("expire $lotId") { expire(lot) }
            throw AuctionExceptions.funExceptionLotClosed(method, lotId)
        }
        return reconciled(lot)
    }

    private fun requireOrb(code: String, method: String) {
        val item = index.item(code) ?: throw CharacterExceptions.funExceptionItemNotFound(method, code)
        if (item.category != Item.CURRENCY) throw AuctionExceptions.funExceptionPriceNotOrb(method, code)
    }

    /** Цена больше нуля и не больше потолка стопки: такую сумму продавец иначе не смог бы получить. */
    private fun requirePrice(price: Long, method: String) {
        if (price <= 0) throw AuctionExceptions.funExceptionPrice(method, price.toString())
        if (price > index.rules.maxStack) throw AuctionExceptions.funExceptionPriceTooHigh(method, index.rules.maxStack.toString())
    }
}

/** Места под лоты героя: базовые сразу, по одному докупается за золото до потолка; [price] - цена следующего, 0 - больше не купить. */
@Serializable
data class AuctionSlots(val used: Int, val limit: Int, val max: Int, val price: Long, val money: Long = 0)
