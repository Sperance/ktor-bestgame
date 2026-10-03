package ru.descend.exileforge.features.data.auction

import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.descend.exileforge.CONST_PAGE_SIZE_DEFAULT
import ru.descend.exileforge.base.exception.BaseRouteExceptions
import ru.descend.exileforge.base.route.BaseRoute
import ru.descend.exileforge.base.route.heroId
import ru.descend.exileforge.base.route.itemId
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.logic.locale.LocaleCache
import ru.descend.exileforge.features.logic.trade.AuctionService

/** Маршруты аукциона. Каждый требует героя: аукцион открывается с уровня правил. */
class AuctionLotRoute(repo: AuctionLotRepository, private val auction: AuctionService) :
    BaseRoute<AuctionLot>(
        repository = repo,
        entitySerializer = AuctionLot.serializer(),
        operations = emptySet(),
    ) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/search") {
            call.respondOk(auction.search(call.heroId, searchFrom(call), call.request.queryParameters["after"], call.queryParam("size", CONST_PAGE_SIZE_DEFAULT)))
        }
        get("/my") {
            call.respondOk(auction.findBySeller(call.heroId))
        }
        get("/history") {
            call.respondOk(auction.history(call.heroId))
        }
        post("/sell/equipment") {
            call.respondOk(auction.sellEquipment(call.heroId, call.itemId, call.queryParam("priceOrb"), call.queryParam("price", 0L)))
        }
        post("/sell/item") {
            call.respondOk(auction.sellItem(call.heroId, call.queryParam("code"), call.queryParam("amount", 1L), call.queryParam("priceOrb"), call.queryParam("price", 0L)))
        }
        get("/slots") {
            call.respondOk(auction.slots(call.heroId))
        }
        post("/buy") {
            call.respondOk(auction.buy(call.heroId, call.queryParam("lotId")))
        }
        post("/extend") {
            call.respondOk(auction.extend(call.heroId, call.queryParam("lotId")))
        }
        get("/price") {
            val params = call.request.queryParameters
            val rarity = params["rarity"]?.let { raw -> Rarity.entries.firstOrNull { it.name == raw } }
            call.respondOk(auction.priceHint(call.heroId, call.queryParam("itemCode"), rarity, call.queryParam("itemLevel", 0)))
        }
        post("/cancel") {
            call.respondOk(auction.cancel(call.heroId, call.queryParam("lotId")))
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
            itemCodes = text("title")?.let { auction.codesMatching(language, it) },
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
