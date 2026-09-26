package features.data.auction

import CONST_PAGE_SIZE_DEFAULT
import base.exception.BaseRouteExceptions
import base.route.BaseRoute
import base.route.heroId
import base.route.itemId
import base.route.queryParam
import base.route.respondOk
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import features.logic.locale.LocaleCache
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Маршруты аукциона. Каждый требует героя: аукцион открывается с уровня правил. */
class AuctionLotRoute(private val repo: AuctionLotRepository) : BaseRoute<AuctionLot>(
    repository = repo,
    entitySerializer = AuctionLot.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/search") {
            call.respondOk(repo.search(call.heroId, searchFrom(call), call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT)))
        }
        get("/my") {
            call.respondOk(repo.findBySeller(call.heroId))
        }
        post("/sell/equipment") {
            call.respondOk(repo.sellEquipment(call.heroId, call.itemId, call.queryParam("priceOrb"), call.queryParam("price", 0L)))
        }
        post("/sell/item") {
            call.respondOk(repo.sellItem(call.heroId, call.queryParam("code"), call.queryParam("amount", 1L), call.queryParam("priceOrb"), call.queryParam("price", 0L)))
        }
        get("/slots") {
            call.respondOk(repo.slots(call.heroId))
        }
        post("/slots") {
            call.respondOk(repo.buySlot(call.heroId))
        }
        post("/buy") {
            call.respondOk(repo.buy(call.heroId, call.queryParam("lotId")))
        }
        post("/cancel") {
            call.respondOk(repo.cancel(call.heroId, call.queryParam("lotId")))
        }
    }

    /** Фильтр витрины из строки запроса; пропущенный параметр - «не фильтровать», незнакомое значение - ошибка. */
    private fun searchFrom(call: ApplicationCall): AuctionSearch {
        val params = call.request.queryParameters
        val language = params["lang"]?.takeIf { it.isNotBlank() } ?: LocaleCache.defaultLanguage()
        fun text(name: String): String? = params[name]?.takeIf { it.isNotBlank() }
        fun <E : Enum<E>> enum(name: String, values: List<E>): E? {
            val raw = text(name) ?: return null
            return values.find { it.name.equals(raw, ignoreCase = true) } ?: throw BaseRouteExceptions.funExceptionQuery("searchFrom", "$name=$raw")
        }
        return AuctionSearch(
            kind = enum("kind", LotKind.entries),
            itemCodes = text("title")?.let { repo.codesMatching(language, it) },
            slot = enum("slot", Slot.entries),
            rarity = enum("rarity", Rarity.entries),
            minItemLevel = text("minItemLevel")?.toIntOrNull(),
            maxItemLevel = text("maxItemLevel")?.toIntOrNull(),
            priceOrb = text("priceOrb"),
            maxPrice = text("maxPrice")?.toLongOrNull(),
            sellerId = text("sellerId"),
            excludeSellerId = text("excludeSellerId"),
        )
    }
}
