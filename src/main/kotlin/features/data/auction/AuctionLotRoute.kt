package features.data.auction

import application.enums.EnumAuctionLotKind
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.BaseRouteExceptions
import CONST_PAGE_SIZE_DEFAULT
import base.route.BaseRoute
import base.route.characterId
import base.route.inventoryId
import base.route.queryParam
import base.route.respondOk
import features.logic.locale.LocaleCache
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Маршруты аукциона. Каждый требует персонажа: аукцион открывается с определённого уровня.
 */
class AuctionLotRoute(
    private val repo: AuctionLotRepository
) : BaseRoute<AuctionLot>(
    repository = repo,
    entitySerializer = AuctionLot.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/search") {
            call.respondOk(repo.search(call.characterId, searchFrom(call), call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT)))
        }
        get("/my") {
            call.respondOk(repo.findBySeller(call.characterId))
        }
        post("/sell/equipment") {
            call.respondOk(repo.sellEquipment(call.characterId, call.inventoryId, call.queryParam("priceOrbId"), call.queryParam("price", 0L)))
        }
        post("/sell/item") {
            call.respondOk(repo.sellItem(call.characterId, call.queryParam("itemId"), call.queryParam("amount", 1L),
                call.queryParam("priceOrbId"), call.queryParam("price", 0L)))
        }
        // 0.34.0: места под лоты - сколько занято, и докупить ещё одно за золото.
        get("/slots") {
            call.respondOk(repo.slots(call.characterId))
        }
        post("/slots") {
            call.respondOk(repo.buySlot(call.characterId))
        }
        post("/buy") {
            call.respondOk(repo.buy(call.characterId, call.queryParam("lotId")))
        }
        post("/cancel") {
            call.respondOk(repo.cancel(call.characterId, call.queryParam("lotId")))
        }
    }

    /**
     * Фильтр витрины из query-параметров. Пропущенный параметр - это "не фильтровать".
     */
    private fun searchFrom(call: ApplicationCall): AuctionSearch {
        val params = call.request.queryParameters
        val language = params["lang"]?.takeIf { it.isNotBlank() } ?: LocaleCache.defaultLanguage()

        fun text(name: String): String? = params[name]?.takeIf { it.isNotBlank() }

        // Незнакомое значение перечисления - ошибка запроса,
        // а не молчаливый пропуск фильтра
        fun <E : Enum<E>> enum(name: String, values: List<E>): E? {
            val raw = text(name) ?: return null
            return values.find { it.name.equals(raw, ignoreCase = true) }
                ?: throw BaseRouteExceptions.funExceptionQuery("searchFrom", "$name=$raw")
        }

        return AuctionSearch(
            kind = enum("kind", EnumAuctionLotKind.entries),
            // Поиск по названию разрешается в коды по словарю выбранного языка
            itemCodes = text("title")?.let { repo.codesMatching(language, it) },
            slot = enum("slot", EnumEquipmentType.entries),
            rarity = enum("rarity", EnumRarity.entries),
            minItemLevel = text("minItemLevel")?.toIntOrNull(),
            maxItemLevel = text("maxItemLevel")?.toIntOrNull(),
            priceOrbId = text("priceOrbId"),
            maxPrice = text("maxPrice")?.toLongOrNull(),
            sellerId = text("sellerId"),
            excludeSellerId = text("excludeSellerId")
        )
    }
}
