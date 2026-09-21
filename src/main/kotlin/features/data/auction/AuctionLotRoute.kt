package features.data.auction

import application.enums.EnumAuctionLotKind
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.BaseRouteExceptions
import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.logic.locale.LocaleCache
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * Маршруты аукциона. Всё, кроме служебного CRUD из [BaseRoute],
 * требует персонажа: аукцион открывается с определённого уровня.
 */
class AuctionLotRoute(
    val repo: AuctionLotRepository
) : BaseRoute<AuctionLot, AuctionLot>(
    repository = repo,
    entitySerializer = AuctionLot.serializer(),
    responseSerializer = AuctionLot.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/search") {
            val characterId = call.queryParam("characterId")
            val page = call.queryParam("page", 0)
            val size = call.queryParam("size", 20)
            val data = repo.search(characterId, searchFrom(call), page, size)
            call.respond(ApiMongoResponse.ok(data))
        }
        get("/my") {
            val characterId = call.queryParam("characterId")
            val data = repo.findBySeller(characterId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/sell/equipment") {
            val characterId = call.queryParam("characterId")
            val inventoryId = call.queryParam("inventoryId")
            val priceOrbId = call.queryParam("priceOrbId")
            val price = call.queryParam("price", 0L)
            val data = repo.sellEquipment(characterId, inventoryId, priceOrbId, price)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/sell/item") {
            val characterId = call.queryParam("characterId")
            val itemId = call.queryParam("itemId")
            val amount = call.queryParam("amount", 1L)
            val priceOrbId = call.queryParam("priceOrbId")
            val price = call.queryParam("price", 0L)
            val data = repo.sellItem(characterId, itemId, amount, priceOrbId, price)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/buy") {
            val characterId = call.queryParam("characterId")
            val lotId = call.queryParam("lotId")
            val data = repo.buy(characterId, lotId)
            call.respond(ApiMongoResponse.ok(data))
        }
        post("/cancel") {
            val characterId = call.queryParam("characterId")
            val lotId = call.queryParam("lotId")
            val data = repo.cancel(characterId, lotId)
            call.respond(ApiMongoResponse.ok(data))
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
