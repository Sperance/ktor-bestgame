package ru.descend.exileforge.features.data.auction
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.roll.ItemInstance
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.exileforge.base.entity.VersionedEntity
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.features.data.hero.Hero

/** Эпоха аукциона (1.74.0: 7 дней, 100 мест, возврат почтой): лоты прежних эпох снимаются при старте сервера. */
const val AUCTION_EPOCH = 1

@Serializable
enum class LotKind { EQUIPMENT, ITEM }

@Serializable
enum class LotStatus { ACTIVE, SOLD, CANCELLED, EXPIRED }

/**
 * Лот аукциона - отдельная коллекция `AuctionLot`. Пока лот на витрине, товар лежит в нём, а не у
 * продавца: копия вещи уходит из документа героя, стопка списывается из сумки. Цена - только в сферах
 * ([priceOrb] - код предмета категории CURRENCY). Поля витрины - снимок вещи на момент выставления,
 * чтобы фильтр работал одним запросом к Mongo без справочников; названий нет - клиент берёт текст по коду.
 * Лот живёт до [expiresAt] (1.30.0): потом он закрывается [LotStatus.EXPIRED], товар возвращается продавцу.
 */
@Serializable
data class AuctionLot(
    var sellerId: String,
    var sellerName: String = "",
    var kind: LotKind = LotKind.EQUIPMENT,
    /** Копия вещи, снятая с продавца; у стопок null. */
    var equipment: ItemInstance? = null,
    /** Код предмета стопки; у вещи пусто. */
    var item: String = "",
    var amount: Long = 1,
    var priceOrb: String = "",
    var price: Long = 0,
    /** Сбор с покупателя золотом (1.13.0), посчитанный при выставлении. */
    var fee: Long = 0,
    var itemCode: String = "",
    var slot: Slot? = null,
    var rarity: Rarity? = null,
    var itemLevel: Int = 0,
    var status: LotStatus = LotStatus.ACTIVE,
    var buyerId: String? = null,
    var closedAt: LocalDateTime? = null,
    /** Когда лот снимается с витрины, мс эпохи UTC (1.30.0); ноль - старый лот, срок выводится из [createdAt]. */
    var expiresAt: Long = 0,
    /** Сделка (1.69.0): имя покупателя, когда продано (мс эпохи UTC) и снимок вещи - для истории; товар уже у покупателя. */
    var buyerName: String = "",
    var soldAt: Long = 0,
    var sold: ItemInstance? = null,
    /** Письмо «лот скоро снимется» ушло (1.74.0); продление его сбрасывает. */
    var warned: Boolean = false,
    /** Эпоха аукциона (1.74.0): лоты прежней эпохи закрываются при старте, товар - продавцу ([AUCTION_EPOCH]). */
    var epoch: Int = 0,
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun isOnSale(now: Long = System.currentTimeMillis()): Boolean = status == LotStatus.ACTIVE && now < expiresAt

    /** Можно ли продлить сейчас (1.74.0): лот на витрине и ему осталось не больше окна продления. */
    fun extendable(window: Long, now: Long = System.currentTimeMillis()): Boolean = isOnSale(now) && expiresAt - now <= window

    companion object {
        fun forEquipment(seller: Hero, item: ItemInstance, template: ItemTemplate, priceOrb: String, price: Long, fee: Long, expiresAt: Long): AuctionLot = AuctionLot(
            sellerId = seller._id, sellerName = seller.name, kind = LotKind.EQUIPMENT, equipment = item.copy(slot = null, socket = null),
            priceOrb = priceOrb, price = price, fee = fee, itemCode = template.code, slot = template.slot, rarity = item.rarity, itemLevel = item.level(template),
            expiresAt = expiresAt, epoch = AUCTION_EPOCH,
        )

        fun forItem(seller: Hero, code: String, amount: Long, priceOrb: String, price: Long, fee: Long, expiresAt: Long): AuctionLot = AuctionLot(
            sellerId = seller._id, sellerName = seller.name, kind = LotKind.ITEM, item = code, amount = amount, priceOrb = priceOrb, price = price, fee = fee, itemCode = code,
            expiresAt = expiresAt, epoch = AUCTION_EPOCH,
        )
    }
}

/** Места под лоты героя: базовые сразу, по одному докупается за золото до потолка; [price] - цена следующего, 0 - больше не купить. */
@Serializable
data class AuctionSlots(val used: Int, val limit: Int)

/** Подсказка цены (1.74.0): медиана [price] за штуку в сфере [priceOrb] по [sales] недавним сделкам. */
@Serializable
data class PriceHint(val priceOrb: String, val price: Long, val sales: Int)
