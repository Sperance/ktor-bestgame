package features.logic.trade

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.data.character.CharacterRepository
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierRoller
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/** Предмет на витрине торговца (с 0.34.0): уже выроленный экземпляр и его цена в золоте. */
@Serializable
data class MerchantOffer(val id: String, val item: CharacterEquipment, val price: Long)

/** Витрина героя: что на ней и когда торговец выложит новую (миллисекунды эпохи). */
@Serializable
data class MerchantStock(val refreshAt: Long = 0, val offers: List<MerchantOffer> = emptyList())

/** Чем кончилась покупка: купленный экземпляр уже в тайнике, и сколько золота осталось. */
@Serializable
data class MerchantPurchase(val item: CharacterEquipment, val money: Long)

/**
 * Витрина торговца - правило сервера (с 0.34.0).
 *
 * У каждого героя своя витрина: раз в [WINDOW_HOURS] часа торговец выкладывает от [MIN_OFFERS] до
 * [MAX_OFFERS] предметов уровня героя ± [LEVEL_SPREAD], магических ([EnumRarity.UNCOMMON]) и - с
 * долей [RARE_SHARE] - редких, без уникальных. Экземпляр роллится при выкладке, поэтому игрок
 * видит ровно то, что купит. Цена - то, что торговец дал бы за такой предмет, умноженное на
 * [MARKUP]: купить и сразу продать всегда в убыток. Досрочно витрину не обновить.
 */
object MerchantRules {
    const val WINDOW_HOURS = 4.0
    const val MIN_OFFERS = 4
    const val MAX_OFFERS = 6
    const val LEVEL_SPREAD = 2
    const val RARE_SHARE = 0.3
    const val MARKUP = 4

    /** Витрина на момент [now]: живая остаётся как есть, истёкшая выкладывается заново. */
    fun stock(current: MerchantStock?, characterId: String, level: Int, now: Long, templates: List<Equipment>, random: Random,
              roll: (Equipment, EnumRarity) -> MutableList<Modifier> = ModifierRoller::roll): MerchantStock {
        if (current != null && now < current.refreshAt) return current
        val pool = templates.filter { it.rarity != EnumRarity.UNIQUE && it.slot != EnumEquipmentType.JEWEL && it.slot != EnumEquipmentType.MAP }
        val near = pool.filter { it.requiredLevel in (level - LEVEL_SPREAD)..(level + LEVEL_SPREAD) }
            .ifEmpty { pool.filter { it.requiredLevel <= level + LEVEL_SPREAD } }
        val offers = if (near.isEmpty()) emptyList() else List(random.nextInt(MIN_OFFERS, MAX_OFFERS + 1)) {
            val template = near[random.nextInt(near.size)]
            val rarity = if (random.nextDouble() < RARE_SHARE) EnumRarity.RARE else EnumRarity.UNCOMMON
            val item = CharacterEquipment(characterId = characterId, equipmentId = template._id, params = roll(template, rarity), rarity = rarity)
            MerchantOffer(ObjectId().toHexString(), item, SellPrice.of(template, item.params, emptyMap()) * MARKUP)
        }
        return MerchantStock(now + (WINDOW_HOURS * 3_600_000).toLong(), offers)
    }
}

/** Торговец: витрина героя и покупка с неё. */
class MerchantService : KoinComponent {
    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()

    suspend fun stock(characterId: String): MerchantStock {
        val method = "merchant"
        val character = characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)
        val stock = MerchantRules.stock(character.merchant, characterId, character.level.toInt(), System.currentTimeMillis(),
            equipmentCache.getCache(), Random.Default)
        if (stock != character.merchant) {
            character.merchant = stock
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return stock
    }

    /** Покупка с витрины: золото уходит торговцу, экземпляр - в тайник, строка - с витрины. */
    suspend fun buy(characterId: String, offerId: String): MerchantPurchase {
        val method = "merchantBuy"
        stock(characterId)
        val character = characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)
        val stock = character.merchant ?: MerchantStock()
        val offer = stock.offers.firstOrNull { it.id == offerId } ?: throw CharacterExceptions.funExceptionOfferNotFound(method, offerId)
        if (character.money < offer.price) throw CharacterExceptions.funExceptionGold(method, offer.price.toString())
        val item = transactionExecute(method) { session ->
            character.money -= offer.price
            character.merchant = stock.copy(offers = stock.offers - offer)
            characters.update(character, session)
            inventory.insert(offer.item.copy(characterId = characterId), session)
        }
        return MerchantPurchase(item, character.money)
    }
}
