package features.data.auction

import CONST_AUCTION_MIN_LEVEL
import application.enums.EnumAuctionLotKind
import application.enums.EnumAuctionLotStatus
import application.enums.EnumCurrencyOrb
import base.exception.model.AuctionExceptions
import base.exception.model.CharacterExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import base.route.PagedMongoResponse
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.now
import features.caches.ItemsCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.inventory.CharacterEquipmentRepository
import features.data.items.Items
import features.logic.locale.LocaleCache
import features.logic.locale.LocaleKey
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Аукцион игроков.
 *
 * Доступен с [CONST_AUCTION_MIN_LEVEL] уровня - и на продажу, и на покупку,
 * и на просмотр витрины.
 *
 * Товар на время торгов лежит в лоте, а не у продавца: выставить один предмет
 * дважды или надеть выставленный нельзя. Оплата, передача товара и закрытие
 * лота идут одной транзакцией, поэтому неудачная проверка не оставляет
 * ни списанных сфер, ни потерянного предмета.
 */
class AuctionLotRepository : BaseRepository<AuctionLot>(
    entityClass = AuctionLot::class
), KoinComponent {
    private val characterRepository: CharacterRepository by inject()
    private val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    private val itemsCache: ItemsCache by inject()

    // Витрина - открытые лоты по порядку _id; места продавца - его открытые лоты
    override val indexes = listOf(IndexSpec.on("status", "_id"), IndexSpec.on("sellerId", "status"), IndexSpec.on("itemCode"))

    // ==================== Витрина ====================

    /**
     * Лоты на витрине по фильтру, постранично.
     */
    suspend fun search(characterId: String, search: AuctionSearch, page: Int, size: Int): PagedMongoResponse<AuctionLot> {
        requireTrader(characterId, "search")
        return findPaged(search.toFilter(), page, size)
    }

    /**
     * Превращает поисковый текст в коды предметов, чьи названия ему подходят.
     *
     * Названий в лотах нет, поэтому текст разрешается по словарю выбранного
     * языка - и уже коды уходят в фильтр Mongo. Так поиск остаётся на сервере
     * и честно работает с пагинацией.
     *
     * @param language язык, на котором игрок видит витрину
     * @throws LocaleExceptions.LocaleException если языка нет в манифесте
     */
    fun codesMatching(language: String, text: String): List<String> {
        val bundle = LocaleCache.bundle(language)

        return bundle.codesMatching(LocaleKey.EQUIPMENT, text) + bundle.codesMatching(LocaleKey.ITEM, text)
    }

    /**
     * Снимает открытые лоты удаляемого персонажа: купить их уже нельзя (продавца нет), отменить
     * некому, а в поиске они висели бы вечно. Закрытые остаются историей покупателей.
     */
    suspend fun deleteActiveBySeller(sellerId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", EnumAuctionLotStatus.ACTIVE.name)))
    }

    /**
     * Лоты персонажа - и активные, и уже закрытые.
     */
    suspend fun findBySeller(characterId: String): List<AuctionLot> {
        requireTrader(characterId, "findBySeller")
        return findByFilter(Filters.eq("sellerId", characterId))
    }

    // ==================== Продажа ====================

    /**
     * Выставляет предмет экипировки.
     *
     * Предмет уходит из инвентаря в лот, поэтому надетым выставить его нельзя -
     * сначала нужно снять.
     *
     * @throws AuctionExceptions.AuctionException если предмет выставить нельзя
     */
    suspend fun sellEquipment(characterId: String, inventoryId: String, priceOrbId: String, price: Long): AuctionLot {
        val seller = requireTrader(characterId, "sellEquipment")
        requirePlace(seller, "sellEquipment")
        requireOrb(priceOrbId, "sellEquipment")
        requirePrice(price, "sellEquipment")

        val item = characterEquipmentRepository.requireOwned(characterId, inventoryId, "sellEquipment")
        if (item.isEquipped() || item.socketCode != null)
            throw AuctionExceptions.funExceptionItemEquipped("sellEquipment", inventoryId)
        val template = characterEquipmentRepository.templateOf(item, "sellEquipment")

        return transactionExecute("auction sellEquipment $inventoryId") { session ->
            // Предмет физически уходит из инвентаря: пока лот висит, им не пользуются
            characterEquipmentRepository.deleteById(item, session)

            insert(AuctionLot.forEquipment(seller, item, template, priceOrbId, price), session)
        }
    }

    /**
     * Выставляет стакающиеся предметы, в том числе сферы.
     *
     * @throws AuctionExceptions.AuctionException если предметы выставить нельзя
     */
    suspend fun sellItem(characterId: String, itemId: String, amount: Long, priceOrbId: String, price: Long): AuctionLot {
        val seller = requireTrader(characterId, "sellItem")
        requirePlace(seller, "sellItem")
        requireOrb(priceOrbId, "sellItem")
        requirePrice(price, "sellItem")
        if (amount <= 0) throw AuctionExceptions.funExceptionAmount("sellItem", amount.toString())

        val item = itemsCache.findById(itemId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("sellItem", itemId)

        return transactionExecute("auction sellItem $itemId") { session ->
            // Предметы списываются со склада продавца и живут в лоте
            characterRepository.spendItem(seller, itemId, amount, session)

            insert(AuctionLot.forItem(seller, item, amount, priceOrbId, price), session)
        }
    }

    // ==================== Покупка и снятие ====================

    /**
     * Покупает лот: сферы уходят продавцу, товар - покупателю.
     *
     * @throws AuctionExceptions.AuctionException если лот купить нельзя
     * @throws CharacterExceptions.CharacterException если у покупателя не хватает сфер
     */
    suspend fun buy(characterId: String, lotId: String): AuctionLot {
        val buyer = requireTrader(characterId, "buy")
        val lot = requireOpenLot(lotId, "buy")

        if (lot.sellerId == characterId) throw AuctionExceptions.funExceptionOwnLot("buy", lotId)

        val seller = characterRepository.findById(lot.sellerId)
            ?: throw CharacterExceptions.funExceptionNotFound("buy", lot.sellerId)

        return transactionExecute("auction buy $lotId") { session ->
            // Оплата: не хватило сфер - транзакция откатится и товар останется на витрине
            characterRepository.spendItem(buyer, lot.priceOrbId, lot.price, session)
            characterRepository.earnItem(seller, lot.priceOrbId, lot.price, session)

            deliver(lot, buyer, session)
            close(lot, EnumAuctionLotStatus.SOLD, characterId, session)
        }
    }

    /**
     * Снимает лот с продажи и возвращает товар продавцу.
     *
     * @throws AuctionExceptions.AuctionException если лот снять нельзя
     */
    suspend fun cancel(characterId: String, lotId: String): AuctionLot {
        val seller = requireTrader(characterId, "cancel")
        val lot = requireOpenLot(lotId, "cancel")

        if (lot.sellerId != characterId) throw AuctionExceptions.funExceptionNotSeller("cancel", lotId)

        return transactionExecute("auction cancel $lotId") { session ->
            deliver(lot, seller, session)
            close(lot, EnumAuctionLotStatus.CANCELLED, null, session)
        }
    }

    // ==================== Внутреннее ====================

    /**
     * Отдаёт товар лота персонажу: покупателю при продаже, продавцу при снятии.
     */
    private suspend fun deliver(lot: AuctionLot, owner: Character, session: ClientSession) {
        when (lot.kind) {
            EnumAuctionLotKind.EQUIPMENT -> {
                val item = lot.equipment
                    ?: throw AuctionExceptions.funExceptionLotBroken("deliver", lot._id)

                item.characterId = owner._id
                item.equippedSlot = null
                item.socketCode = null
                // Документ вставляется в инвентарь заново, история версий начинается с нуля
                item.version = 0
                item.deleted = false
                item.updatedAt = LocalDateTime.now()

                characterEquipmentRepository.insert(item, session)
            }

            EnumAuctionLotKind.ITEM -> characterRepository.earnItem(owner, lot.itemId, lot.amount, session)
        }
    }

    /**
     * Закрывает лот: товара в нём больше нет, на витрину он не вернётся.
     */
    private suspend fun close(
        lot: AuctionLot,
        status: EnumAuctionLotStatus,
        buyerId: String?,
        session: ClientSession
    ): AuctionLot {
        lot.status = status
        lot.buyerId = buyerId
        lot.closedAt = LocalDateTime.now()
        lot.equipment = null

        update(lot, session)
        return lot
    }

    /**
     * Персонаж, которому аукцион доступен.
     *
     * @throws AuctionExceptions.AuctionException если уровень слишком низкий
     */
    // ==================== Места под лоты (0.34.0) ====================

    /** Сколько лотов героя сейчас на витрине и сколько мест у него всего. */
    suspend fun slots(characterId: String): AuctionSlots {
        val seller = requireTrader(characterId, "slots")
        return AuctionSlots.of(seller.auctionSlots, active(seller._id))
    }

    /** Докупает одно место под лот за золото; цена растёт с каждым купленным. */
    suspend fun buySlot(characterId: String): AuctionSlots {
        val method = "buySlot"
        val seller = requireTrader(characterId, method)
        if (AuctionSlots.BASE + seller.auctionSlots >= AuctionSlots.MAX) throw AuctionExceptions.funExceptionSlotsMax(method, AuctionSlots.MAX.toString())
        val price = AuctionSlots.price(seller.auctionSlots)
        if (seller.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        transactionExecute(method) { session ->
            seller.money -= price
            seller.auctionSlots += 1
            characterRepository.update(seller, session)
        }
        return AuctionSlots.of(seller.auctionSlots, active(seller._id), seller.money)
    }

    private suspend fun active(sellerId: String): Int =
        count(Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", EnumAuctionLotStatus.ACTIVE.name))).toInt()

    /** Новый лот - только на свободное место; выставленные раньше сверх лимита остаются. */
    private suspend fun requirePlace(seller: Character, method: String) {
        val limit = AuctionSlots.BASE + seller.auctionSlots
        if (active(seller._id) >= limit) throw AuctionExceptions.funExceptionLotLimit(method, limit.toString())
    }

    private suspend fun requireTrader(characterId: String, method: String): Character {
        val character = characterRepository.requireCharacter(characterId, method)
        if (character.level < CONST_AUCTION_MIN_LEVEL)
            throw AuctionExceptions.funExceptionLevel(method, "${character.level}, need $CONST_AUCTION_MIN_LEVEL")

        return character
    }

    private suspend fun requireOpenLot(lotId: String, method: String): AuctionLot {
        val lot = findById(lotId) ?: throw AuctionExceptions.funExceptionLotNotFound(method, lotId)
        if (!lot.isOnSale()) throw AuctionExceptions.funExceptionLotClosed(method, lotId)
        return lot
    }

    /**
     * Цена назначается только в сферах, поэтому предмет цены обязан быть сферой.
     */
    private fun requireOrb(itemId: String, method: String): Items {
        val item = itemsCache.findById(itemId)
            ?: throw CharacterExceptions.funExceptionItemNotFound(method, itemId)

        if (item.category != EnumCurrencyOrb.CATEGORY)
            throw AuctionExceptions.funExceptionPriceNotOrb(method, item.code)

        return item
    }

    private fun requirePrice(price: Long, method: String) {
        if (price <= 0) throw AuctionExceptions.funExceptionPrice(method, price.toString())
    }

    /** То же для предметов, что сейчас лежат на аукционе: лот держит экземпляр у себя. */
    suspend fun pruneMissingModifiers(modifierCodes: Collection<String>, session: ClientSession): Long {
        if (modifierCodes.isEmpty()) return 0
        val stale = org.bson.Document("modifierCode", org.bson.Document("\$nin", modifierCodes.toList()))
        return collection.updateMany(session, Filters.exists("equipment.params", true),
            org.bson.Document("\$pull", org.bson.Document("equipment.params", stale))).modifiedCount
    }
}

/**
 * Места под лоты героя (с 0.34.0): [BASE] сразу, по одному докупается за золото до [MAX]. Цена
 * первого - [FIRST_PRICE], каждое следующее в [GROWTH] раза дороже.
 *
 * @property used сколько лотов героя сейчас на витрине
 * @property price сколько стоит следующее место; 0, когда больше не купить
 * @property money золото героя после покупки; 0 в ответе на чтение
 */
@kotlinx.serialization.Serializable
data class AuctionSlots(val used: Int, val limit: Int, val max: Int, val price: Long, val money: Long = 0) {
    companion object {
        const val BASE = 5
        const val MAX = 20
        const val FIRST_PRICE = 500.0
        const val GROWTH = 1.5

        fun price(bought: Int): Long = Math.round(FIRST_PRICE * Math.pow(GROWTH, bought.toDouble()))

        fun of(bought: Int, used: Int, money: Long = 0): AuctionSlots {
            val limit = BASE + bought
            return AuctionSlots(used, limit, MAX, if (limit >= MAX) 0 else price(bought), money)
        }
    }
}
