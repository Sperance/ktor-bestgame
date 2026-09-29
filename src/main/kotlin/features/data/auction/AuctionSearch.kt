package features.data.auction

import com.mongodb.client.model.Filters
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson

/**
 * Фильтр витрины. Пустой фильтр - вся витрина: закрытые лоты не показываются никогда. Все поля
 * снимаются с лота, поэтому поиск укладывается в один запрос к Mongo.
 */
@Serializable
data class AuctionSearch(
    val kind: LotKind? = null,
    /** Коды предметов, подходящих под поисковый текст; пустой список отсекает всю витрину. */
    val itemCodes: List<String>? = null,
    val slot: Slot? = null,
    val rarity: Rarity? = null,
    val minItemLevel: Int? = null,
    val maxItemLevel: Int? = null,
    val priceOrb: String? = null,
    val maxPrice: Long? = null,
    val sellerId: String? = null,
    val excludeSellerId: String? = null,
) {
    /** Фильтр на момент [now]: истёкший лот (1.30.0) не показывается, даже если его ещё не закрыли. */
    fun toFilter(now: Long = System.currentTimeMillis()): Bson {
        val conditions = mutableListOf<Bson>(Filters.eq("status", LotStatus.ACTIVE.name),
            Filters.or(Filters.exists("expiresAt", false), Filters.eq("expiresAt", 0L), Filters.gt("expiresAt", now)))
        kind?.let { conditions.add(Filters.eq("kind", it.name)) }
        slot?.let { conditions.add(Filters.eq("slot", it.name)) }
        rarity?.let { conditions.add(Filters.eq("rarity", it.name)) }
        priceOrb?.let { conditions.add(Filters.eq("priceOrb", it)) }
        sellerId?.let { conditions.add(Filters.eq("sellerId", it)) }
        excludeSellerId?.let { conditions.add(Filters.ne("sellerId", it)) }
        maxPrice?.let { conditions.add(Filters.lte("price", it)) }
        minItemLevel?.let { conditions.add(Filters.gte("itemLevel", it)) }
        maxItemLevel?.let { conditions.add(Filters.lte("itemLevel", it)) }
        itemCodes?.let { conditions.add(Filters.`in`("itemCode", it)) }
        return Filters.and(conditions)
    }
}
