package ru.descend.exileforge.features.data.auction
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.toList
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec

/**
 * Лоты аукциона: индексы и выборки по продавцу, сделкам, сроку и эпохе. Торговля - в
 * [ru.descend.exileforge.features.logic.trade.AuctionService].
 */
class AuctionLotRepository : BaseRepository<AuctionLot>(AuctionLot::class) {
    override val indexes = listOf(IndexSpec.on("status", "_id"), IndexSpec.on("sellerId", "status"), IndexSpec.on("itemCode"), IndexSpec.on("status", "expiresAt"), IndexSpec.on("buyerId", "soldAt"), IndexSpec.on("sellerId", "soldAt"))

    /** Открытые лоты удаляемого героя снимаются: купить их уже нельзя, отменить некому. */
    suspend fun deleteActiveBySeller(sellerId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", LotStatus.ACTIVE.name)))
    }

    /** Все лоты продавца, любого статуса. */
    suspend fun findBySeller(sellerId: String): List<AuctionLot> = findByFilter(Filters.eq("sellerId", sellerId))

    /** Сколько лотов продавца на витрине. */
    suspend fun countActive(sellerId: String): Long = count(Filters.and(Filters.eq("sellerId", sellerId), Filters.eq("status", LotStatus.ACTIVE.name)))

    /** Сделки героя - его продажи и покупки - не раньше [since], новые первыми. */
    suspend fun findDeals(heroId: String, since: Long): List<AuctionLot> = findByFilter(
        Filters.and(
            Filters.eq("status", LotStatus.SOLD.name),
            Filters.gte("soldAt", since),
            Filters.or(Filters.eq("sellerId", heroId), Filters.eq("buyerId", heroId)),
        ),
    ).sortedByDescending { it.soldAt }

    /** Продажи базы [itemCode] не раньше [since]; редкость [rarity] и уровни [levels] - если заданы. */
    suspend fun findSales(itemCode: String, rarity: String?, levels: IntRange?, since: Long): List<AuctionLot> {
        val filters = listOfNotNull(
            Filters.eq("status", LotStatus.SOLD.name),
            Filters.gte("soldAt", since),
            Filters.eq("itemCode", itemCode),
            rarity?.let { Filters.eq("rarity", it) },
            levels?.let { Filters.and(Filters.gte("itemLevel", it.first), Filters.lte("itemLevel", it.last)) },
        )
        return findByFilter(Filters.and(filters))
    }

    /** Открытые лоты, чей срок вышел к [now]: продавца [sellerId] или, без него, все. */
    suspend fun findExpired(now: Long, sellerId: String?): List<AuctionLot> {
        val scope = listOfNotNull(Filters.eq("status", LotStatus.ACTIVE.name), sellerId?.let { Filters.eq("sellerId", it) })
        return findByFilter(Filters.and(scope + Filters.lte("expiresAt", now)))
    }

    /** Открытые лоты всех продавцов, чей срок вышел к [now], пачкой по [limit]. */
    suspend fun findExpired(now: Long, limit: Int): List<AuctionLot> = collection.find(readFilter(Filters.and(Filters.eq("status", LotStatus.ACTIVE.name), Filters.lte("expiresAt", now))))
        .limit(limit).toList()

    /** Открытые лоты без предупреждения, чей срок выйдет после [now], но не позже [until], пачкой по [limit]. */
    suspend fun findExpiring(now: Long, until: Long, limit: Int): List<AuctionLot> = collection.find(
        readFilter(
            Filters.and(
                Filters.eq("status", LotStatus.ACTIVE.name),
                Filters.ne("warned", true),
                Filters.gt("expiresAt", now),
                Filters.lte("expiresAt", until),
            ),
        ),
    ).limit(limit).toList()

    /** Открытые лоты, которые уже не продать: цена не в валютах [currencies] или стопка предмета вне [items]. */
    suspend fun findUntradable(currencies: List<String>, items: Collection<String>): List<AuctionLot> {
        val stale = Filters.or(
            Filters.nin("priceOrb", currencies),
            Filters.and(Filters.eq("kind", LotKind.ITEM.name), Filters.nin("item", items)),
        )
        return findByFilter(Filters.and(Filters.eq("status", LotStatus.ACTIVE.name), stale))
    }

    /** Открытые лоты эпох раньше [AUCTION_EPOCH]. */
    suspend fun findOldEpoch(): List<AuctionLot> = findByFilter(Filters.and(Filters.eq("status", LotStatus.ACTIVE.name), Filters.lt("epoch", AUCTION_EPOCH)))
}
