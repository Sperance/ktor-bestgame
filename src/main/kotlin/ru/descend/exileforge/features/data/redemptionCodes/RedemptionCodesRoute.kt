package ru.descend.exileforge.features.data.redemptionCodes
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import ru.descend.exileforge.base.route.BaseRoute
import ru.descend.exileforge.base.route.Crud
import ru.descend.exileforge.base.route.heroId
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.features.logic.hero.respondWithHero
import ru.descend.exileforge.server.addons.REDEEM_LIMIT

/** Промокоды: администратор их заводит и удаляет, игрок - погашает. */
class RedemptionCodesRoute(private val repo: RedemptionCodesRepository, private val service: ru.descend.exileforge.features.logic.redemption.RedemptionService) :
    BaseRoute<RedemptionCodes>(
        repository = repo,
        entitySerializer = RedemptionCodes.serializer(),
        operations = setOf(Crud.READ, Crud.CREATE, Crud.DELETE),
    ) {
    override fun additionalRoutes(route: Route) = with(route) {
        rateLimit(REDEEM_LIMIT) {
            post("/redeem") {
                call.respondWithHero(service.redeem(call.heroId, call.queryParam("code")))
            }
        }
    }
}
