package features.data.redemptionCodes

import base.route.BaseRoute
import base.route.Crud
import base.route.heroId
import base.route.queryParam
import features.logic.hero.respondWithHero
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** Промокоды: администратор их заводит и удаляет, игрок - погашает. */
class RedemptionCodesRoute(private val repo: RedemptionCodesRepository) : BaseRoute<RedemptionCodes>(
    repository = repo,
    entitySerializer = RedemptionCodes.serializer(),
    operations = setOf(Crud.READ, Crud.CREATE, Crud.DELETE),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        post("/redeem") {
            call.respondWithHero(repo.redeem(call.heroId, call.queryParam("code")))
        }
    }
}
