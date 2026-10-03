package ru.descend.exileforge.features.logic.trade

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.text.LocaleKey
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDateTime
import ru.descend.exileforge.base.exception.model.AuctionExceptions
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.route.CursorPage
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.extensions.printLog
import ru.descend.exileforge.features.data.auction.AuctionLot
import ru.descend.exileforge.features.data.auction.AuctionLotRepository
import ru.descend.exileforge.features.data.auction.AuctionSearch
import ru.descend.exileforge.features.data.auction.AuctionSlots
import ru.descend.exileforge.features.data.auction.LotKind
import ru.descend.exileforge.features.data.auction.LotStatus
import ru.descend.exileforge.features.data.auction.PriceHint
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.mail.MailAttachment
import ru.descend.exileforge.features.data.mail.MailRepository
import ru.descend.exileforge.features.logic.hero.HeroLocks
import ru.descend.exileforge.features.logic.hero.Stash
import ru.descend.exileforge.features.logic.locale.LocaleCache

/**
 * Аукцион игроков. Товар на время торгов лежит в лоте: выставить одну вещь дважды или надеть
 * выставленную нельзя. Оплата, передача товара и закрытие лота идут одной транзакцией.
 *
 * С 1.30.0 лот стоит на витрине `auction.lotDays` дней: истёкший закрывается [LotStatus.EXPIRED] лениво - на
 * витрине, в своих лотах и на любом запросе продавца, - товар возвращается продавцу, сбор не возвращается.
 * Стопки в сумке не ограничены, так что оплата и товар-стопка доходят целиком. Вещь лота сверяется с контентом,
 * когда лот показывают. Выборки лотов - в [AuctionLotRepository].
 */
class AuctionService(
    private val lots: AuctionLotRepository,
    private val heroes: HeroRepository,
    private val mail: MailRepository,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index
    private val rules get() = index.rules.auction

    /** Витрина по курсору (1.62.0): страница после лота [after], без `skip`. */
    suspend fun search(heroId: String, search: AuctionSearch, after: String?, size: Int): CursorPage<AuctionLot> {
        requireTrader(heroId, "search")
        search.priceOrb?.let { requireOrb(it, "search") }
        val found = lots.findAfter(search.toFilter(currencies = currencyCodes(), items = index.items.keys), after, size)
        return found.copy(items = found.items.map { reconciled(it) })
    }

    /**
     * Уборка на старте: открытые лоты, которые уже не продать - стопка снятого или неизвестного предмета, цена не в валюте
     * аукциона, - закрываются, товар возвращается продавцу (снятый код - своей заменой). Повторный запуск ничего не находит.
     */
    suspend fun closeUntradable() {
        val stale = lots.findUntradable(currencyCodes(), index.items.keys)
        stale.forEach { lot -> quietly("close untradable ${lot._id}") { HeroLocks.withLock(lot.sellerId) { expire(lot, LotStatus.CANCELLED) } } }
        if (stale.isNotEmpty()) printLog("  → ${stale.size} untradable auction lots closed")
    }

    private fun currencyCodes(): List<String> = rules.currencies.map { it.name }

    /** Поисковый текст - в коды предметов по словарю языка: названий в лотах нет, а поиск остаётся на сервере. */
    fun codesMatching(language: String, text: String): List<String> {
        val bundle = LocaleCache.bundle(language)
        return bundle.codesMatching(LocaleKey.EQUIPMENT, text) + bundle.codesMatching(LocaleKey.ITEM, text)
    }

    suspend fun findBySeller(heroId: String): List<AuctionLot> {
        requireTrader(heroId, "findBySeller")
        return lots.findBySeller(heroId).map { reconciled(it) }
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
            lots.insert(AuctionLot.forEquipment(seller, item, template, priceOrb, price, fee(priceOrb, price), expiry()), session)
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
        // Сундуки-добыча продаются только здесь (1.72.0): торговцу их не сдать.
        index.item(code) ?: throw CharacterExceptions.funExceptionItemNotFound(method, code)
        seller.spend(code, amount, method)
        return transactionExecute("auction $method $code") { session ->
            heroes.update(seller, session)
            lots.insert(AuctionLot.forItem(seller, code, amount, priceOrb, price, fee(priceOrb, price), expiry()), session)
        }
    }

    /**
     * Покупка: сферы уходят продавцу, товар - покупателю, сбор золотом сгорает; не хватило сфер или золота -
     * отказ, ничего не списано, товар остаётся на витрине.
     */
    suspend fun buy(heroId: String, lotId: String): AuctionLot {
        val method = "buy"
        val buyer = requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId == heroId) throw AuctionExceptions.funExceptionOwnLot(method, lotId)
        if (lot.kind == LotKind.ITEM && index.item(lot.item) == null) throw AuctionExceptions.funExceptionLotClosed(method, lotId)
        requireOrb(lot.priceOrb, method)
        val seller = heroes.findById(lot.sellerId) ?: throw CharacterExceptions.funExceptionNotFound(method, lot.sellerId)
        val fee = lot.fee
        if (buyer.money < fee) throw CharacterExceptions.funExceptionGold(method, fee.toString())
        buyer.spend(lot.priceOrb, lot.price, method)
        buyer.pay(fee)
        seller.earn(lot.priceOrb, lot.price)
        deliver(lot, buyer)
        buyer.count(Counter.AUCTION_BOUGHT)
        seller.count(Counter.AUCTION_SOLD)
        lot.buyerName = buyer.name
        lot.soldAt = System.currentTimeMillis()
        lot.sold = lot.equipment
        return transactionExecute("auction $method $lotId") { session ->
            heroes.update(buyer, session)
            heroes.update(seller, session)
            close(lot, LotStatus.SOLD, heroId, session)
        }
    }

    /** История сделок героя (1.69.0): его продажи и покупки за `auction.historyDays` дней, новые первыми. */
    suspend fun history(heroId: String): List<AuctionLot> {
        requireTrader(heroId, "history")
        val since = System.currentTimeMillis() - rules.historyDays * ru.descend.exileforge.extensions.Millis.DAY
        return lots.findDeals(heroId, since)
    }

    suspend fun cancel(heroId: String, lotId: String): AuctionLot {
        val method = "cancel"
        val seller = requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId != heroId) throw AuctionExceptions.funExceptionNotSeller(method, lotId)
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
            LotKind.ITEM -> owner.earn(lot.item, lot.amount)
        }
    }

    /** Товар обратно продавцу: вещь - в тайник или переполнение, не торговцу; стопка - в сумку. */
    private fun giveBack(lot: AuctionLot, owner: Hero) {
        when (lot.kind) {
            LotKind.EQUIPMENT -> Stash.giveBack(owner, goods(lot), index)
            LotKind.ITEM -> owner.earn(index.rules.retired[lot.item] ?: lot.item, lot.amount)
        }
    }

    private fun goods(lot: AuctionLot) = (lot.equipment ?: throw AuctionExceptions.funExceptionLotBroken("deliver", lot._id)).copy(slot = null, socket = null)

    private fun expiry(): Long = System.currentTimeMillis() + rules.lotMillis

    private companion object {
        const val MAIL_EXPIRED = "mail.auction_expired"
        const val MAIL_EXPIRING = "mail.auction_expiring"
    }

    /**
     * Лот, каким его показывают: вещь сверена с контентом так же, как её сверит запись героя. Изменилось
     * что-то - лот переписывается, иначе следующий показ докатил бы вещь иначе; сбой записи показа не срывает.
     */
    private suspend fun reconciled(lot: AuctionLot): AuctionLot {
        if (reconcileItem(lot)) quietly("reconcile ${lot._id}") { transactionExecute("auction reconcile ${lot._id}") { session -> lots.update(lot, session) } }
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
     * сгорел при покупке и не возвращается.
     * Стопка, которой продавцу некуда лечь, ждёт на витрине, пока место не появится: сгореть она не должна.
     */
    private suspend fun expireDue(sellerId: String?) {
        lots.findExpired(System.currentTimeMillis(), sellerId).forEach { lot ->
            quietly("expire ${lot._id}") { expire(lot) }
        }
    }

    /** Просроченные лоты всех продавцов пачкой по [limit] (1.53.0), каждый под очередью своего продавца; зовёт [AuctionExpiry]. */
    suspend fun expireDue(limit: Int) {
        val due = lots.findExpired(System.currentTimeMillis(), limit)
        due.forEach { lot -> quietly("expire ${lot._id}") { HeroLocks.withLock(lot.sellerId) { expire(lot) } } }
    }

    /**
     * Лот уходит с витрины. Истёкший (1.74.0) возвращается продавцу письмом с товаром - тем же роллом, тайник не
     * переполняется молча; снятый по другой причине (непродаваемый, смена эпохи) - сразу в тайник или сумку.
     */
    private suspend fun expire(lot: AuctionLot, status: LotStatus = LotStatus.EXPIRED) {
        val seller = heroes.findById(lot.sellerId)
        val byMail = status == LotStatus.EXPIRED && seller != null
        if (seller != null && !byMail) giveBack(lot, seller)
        val parcel = if (byMail) returned(lot) else null
        transactionExecute("auction expire ${lot._id}") { session ->
            if (byMail) {
                mail.system(seller!!.userId, MAIL_EXPIRED, listOf(lot.itemCode, lot.amount.toString()), parcel!!, session)
            } else {
                seller?.let { heroes.update(it, session) }
            }
            close(lot, status, null, session)
        }
    }

    /** Товар лота во вложении письма: вещь как есть, стопка - по нынешнему коду. */
    private fun returned(lot: AuctionLot): MailAttachment = when (lot.kind) {
        LotKind.EQUIPMENT -> MailAttachment(instances = listOf(goods(lot)))
        LotKind.ITEM -> MailAttachment(items = mapOf((index.rules.retired[lot.item] ?: lot.item) to lot.amount))
    }

    /**
     * Продление (1.74.0): автор в последние часы лота ставит его на витрину ещё на `auction.lotDays` дней - сколько угодно раз.
     */
    suspend fun extend(heroId: String, lotId: String): AuctionLot {
        val method = "extend"
        requireTrader(heroId, method)
        val lot = requireOpenLot(lotId, method)
        if (lot.sellerId != heroId) throw AuctionExceptions.funExceptionNotSeller(method, lotId)
        if (!lot.extendable(rules.extendWindowMillis)) throw AuctionExceptions.funExceptionNotExtendable(method, lotId)
        lot.expiresAt += rules.lotMillis
        lot.warned = false
        return transactionExecute("auction $method $lotId") { session ->
            lots.update(lot, session)
            lot
        }
    }

    /** Письмо «лот снимется через сутки» (1.74.0) пачкой по [limit]; зовёт [AuctionExpiry]. */
    suspend fun warnDue(limit: Int) {
        val now = System.currentTimeMillis()
        val due = lots.findExpiring(now, now + rules.extendWindowMillis, limit)
        due.forEach { lot ->
            quietly("warn ${lot._id}") {
                val seller = heroes.findById(lot.sellerId) ?: return@quietly
                lot.warned = true
                transactionExecute("auction warn ${lot._id}") { session ->
                    mail.system(seller.userId, MAIL_EXPIRING, listOf(lot.itemCode, lot.amount.toString()), MailAttachment(), session)
                    lots.update(lot, session)
                }
            }
        }
    }

    /** Смена эпохи (1.74.0): лоты прежних правил снимаются, товар - продавцам; повторный старт ничего не находит. */
    suspend fun closeOldEpoch() {
        val old = lots.findOldEpoch()
        old.forEach { lot -> quietly("close old epoch ${lot._id}") { HeroLocks.withLock(lot.sellerId) { expire(lot, LotStatus.CANCELLED) } } }
        if (old.isNotEmpty()) printLog("  → ${old.size} auction lots of the previous epoch returned")
    }

    /**
     * Подсказка цены (1.74.0): медиана продаж той же базы и редкости близкого уровня за `auction.historyDays` дней - в сфере,
     * которой их продавали чаще; меньше `auction.priceSales` сделок - подсказки нет.
     */
    suspend fun priceHint(heroId: String, itemCode: String, rarity: Rarity?, itemLevel: Int): PriceHint? {
        requireTrader(heroId, "priceHint")
        val since = System.currentTimeMillis() - rules.historyDays * ru.descend.exileforge.extensions.Millis.DAY
        val levels = if (itemLevel > 0) (itemLevel - rules.priceLevelSpread)..(itemLevel + rules.priceLevelSpread) else null
        val sales = lots.findSales(itemCode, rarity?.name, levels, since)
        val (orb, inOrb) = sales.groupBy { it.priceOrb }.maxByOrNull { it.value.size } ?: return null
        if (inOrb.size < rules.priceSales) return null
        val unit = inOrb.map { it.price.toDouble() / it.amount.coerceAtLeast(1) }.sorted()
        val median = if (unit.size % 2 == 1) unit[unit.size / 2] else (unit[unit.size / 2 - 1] + unit[unit.size / 2]) / 2
        return PriceHint(orb, kotlin.math.max(1L, kotlin.math.round(median).toLong()), inOrb.size)
    }

    /** Попутная работа (срок, сверка): гонка версий или сбой базы её откладывает до следующего запроса, но не срывает запрос. */
    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printLog("[Auction] $what: ${e.message}")
        }
    }

    private suspend fun close(lot: AuctionLot, status: LotStatus, buyerId: String?, session: ClientSession): AuctionLot {
        lot.status = status
        lot.buyerId = buyerId
        lot.closedAt = LocalDateTime.now()
        lot.equipment = null
        lots.update(lot, session)
        return lot
    }

    /** Сколько лотов героя на витрине и сколько мест всего (1.44.0: мест поровну у всех, не покупаются). */
    suspend fun slots(heroId: String): AuctionSlots {
        val seller = requireTrader(heroId, "slots")
        return slotsOf(seller, active(seller._id))
    }

    private fun slotsOf(@Suppress("UNUSED_PARAMETER") seller: Hero, used: Int): AuctionSlots = AuctionSlots(used, rules.slots)

    private suspend fun active(sellerId: String): Int = lots.countActive(sellerId).toInt()

    private suspend fun requirePlace(seller: Hero, method: String) {
        if (active(seller._id) >= rules.slots) throw AuctionExceptions.funExceptionLotLimit(method, rules.slots.toString())
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
        val lot = lots.findById(lotId) ?: throw AuctionExceptions.funExceptionLotNotFound(method, lotId)
        if (!lot.isOnSale()) {
            if (lot.status == LotStatus.ACTIVE) quietly("expire $lotId") { expire(lot) }
            throw AuctionExceptions.funExceptionLotClosed(method, lotId)
        }
        return reconciled(lot)
    }

    private fun requireOrb(code: String, method: String) {
        val item = index.item(code) ?: throw CharacterExceptions.funExceptionItemNotFound(method, code)
        // Цена (1.65.0) - только базовыми сферами ремесла правил аукциона; товаром идёт любой предмет.
        if (item.category != Item.CURRENCY || !index.rules.auction.trades(code)) throw AuctionExceptions.funExceptionPriceNotOrb(method, code)
    }

    /** Цена больше нуля. */
    private fun requirePrice(price: Long, method: String) {
        if (price <= 0) throw AuctionExceptions.funExceptionPrice(method, price.toString())
    }
}
