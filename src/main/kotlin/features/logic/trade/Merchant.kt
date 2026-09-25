package features.logic.trade

import application.enums.EnumRarity
import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierRoller
import features.logic.pools.Pools
import features.logic.pools.Weighted
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
 * [MAX_OFFERS] предметов уровня героя ± [LEVEL_SPREAD] по весам [RARITIES] - с 0.43.0 и белые, без
 * аффиксов, под сферы (самоцвет белым не бывает, см. `Jewels`), - из пулов [POOLS]. Экземпляр роллится при выкладке, поэтому игрок
 * видит ровно то, что купит. Цена - то, что торговец дал бы за такой предмет, умноженное на
 * [MARKUP]: купить и сразу продать всегда в убыток. Досрочно витрину не обновить.
 */
object MerchantRules {
    const val WINDOW_HOURS = 4.0
    const val MIN_OFFERS = 12
    const val MAX_OFFERS = 16
    const val LEVEL_SPREAD = 2

    /** Сколько каких редкостей на витрине (с 0.43.0): половина белых, треть с лишним волшебных, остальное редкие. */
    val RARITIES = listOf(EnumRarity.COMMON to 50, EnumRarity.UNCOMMON to 35, EnumRarity.RARE to 15)

    private fun rarity(random: Random): EnumRarity {
        var point = random.nextInt(RARITIES.sumOf { it.second })
        RARITIES.forEach { (rarity, weight) -> point -= weight; if (point < 0) return rarity }
        return RARITIES.first().first
    }
    const val MARKUP = 3

    /** Пулы экипировки, из которых торговец выкладывает товар (с 0.39.0). */
    val POOLS = listOf("merchant")

    /** Витрина на момент [now]: живая остаётся как есть, истёкшая выкладывается заново. */
    fun stock(current: MerchantStock?, characterId: String, level: Int, now: Long, pool: List<Weighted<Equipment>>, random: Random,
              roll: (Equipment, EnumRarity) -> MutableList<Modifier> = ModifierRoller::roll,
              affix: (Modifier) -> Boolean = ModifierRoller::isAffix): MerchantStock {
        if (current != null && now < current.refreshAt) return current
        val near = pool.filter { it.value.requiredLevel in (level - LEVEL_SPREAD)..(level + LEVEL_SPREAD) }
            .ifEmpty { pool.filter { it.value.requiredLevel <= level + LEVEL_SPREAD } }
        val offers = if (near.isEmpty()) emptyList() else List(random.nextInt(MIN_OFFERS, MAX_OFFERS + 1)) {
            val template = Pools.draw(near, random) ?: near.first().value
            val wanted = features.logic.equipment.Jewels.rarity(template, rarity(random))
            // Волшебная или редкая вещь без единого аффикса (0.49.1) - брак ролла, на витрину ему нельзя:
            // второй заход, а если и он пуст - вещь выкладывается белой, какой она и вышла.
            var params = roll(template, wanted)
            if (wanted != EnumRarity.COMMON && params.none(affix)) params = roll(template, wanted)
            val rarity = if (wanted != EnumRarity.COMMON && params.none(affix)) EnumRarity.COMMON else wanted
            val item = CharacterEquipment(characterId = characterId, equipmentId = template._id, params = params, rarity = rarity)
            MerchantOffer(ObjectId().toHexString(), item, SellPrice.of(template, rarity, item.params, emptyMap()) * MARKUP)
        }
        return MerchantStock(now + (WINDOW_HOURS * 3_600_000).toLong(), offers)
    }
}

/** Торговец: витрина героя и покупка с неё. */
class MerchantService : KoinComponent {
    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()

    suspend fun stock(characterId: String): MerchantStock = restock(characters.requireCharacter(characterId, "merchant"))

    /** Витрина героя на сейчас; сменившаяся записывается, и [character] в памяти идёт в ногу с базой. */
    private suspend fun restock(character: Character): MerchantStock {
        val stock = MerchantRules.stock(character.merchant, character._id, character.level.toInt(), System.currentTimeMillis(),
            equipmentCache.pool(MerchantRules.POOLS), Random.Default)
        if (stock != character.merchant) {
            character.merchant = stock
            transactionExecute("merchant") { session -> characters.update(character, session) }
        }
        return stock
    }

    /** Покупка с витрины: золото уходит торговцу, экземпляр - в тайник, строка - с витрины. */
    suspend fun buy(characterId: String, offerId: String): MerchantPurchase {
        val method = "merchantBuy"
        val character = characters.requireCharacter(characterId, method)
        val stock = restock(character)
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
