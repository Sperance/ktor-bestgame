package features.data.guild

import CONST_PAGE_SIZE_DEFAULT
import base.exception.BaseRouteExceptions
import base.route.BaseRoute
import base.route.heroId
import base.route.optionalParam
import base.route.queryParam
import base.route.respondOk
import com.sperance.exileforge.rules.content.GuildMode
import features.logic.hero.respondWithHero
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import server.addons.AppJson

/** Маршруты гильдии (`/api/v1/guild/...`): каждый от имени героя `heroId`, команды отвечают со снимком героя. */
class GuildRoute(private val repo: GuildRepository) : BaseRoute<Guild>(
    repository = repo,
    entitySerializer = Guild.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/mine") { call.respondOk(repo.mine(call.heroId)) }
        get("/search") {
            call.respondOk(repo.search(call.heroId, call.optionalParam("text"), call.optionalParam("faction"), call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT)))
        }
        post("/create") {
            call.respondWithHero(repo.create(
                call.heroId, call.queryParam("name"), call.queryParam("tag"), call.queryParam("faction"), call.queryParam("emblem"), call.queryParam("color"),
                call.mode() ?: GuildMode.OPEN, call.queryParam("minLevel", 1),
            ))
        }
        post("/join") { call.respondWithHero(repo.join(call.heroId, call.queryParam("guildId"))) }
        post("/apply") { call.respondWithHero(repo.submitApplication(call.heroId, call.queryParam("guildId"))) }
        post("/applications/accept") { call.respondWithHero(repo.acceptApplication(call.heroId, call.queryParam("applicantId"))) }
        post("/applications/decline") { call.respondWithHero(repo.declineApplication(call.heroId, call.queryParam("applicantId"))) }
        post("/invite") { call.respondWithHero(repo.invite(call.heroId, call.queryParam("name"))) }
        post("/invites/accept") { call.respondWithHero(repo.acceptInvite(call.heroId, call.queryParam("guildId"))) }
        post("/invites/decline") { call.respondWithHero(repo.declineInvite(call.heroId, call.queryParam("guildId"))) }
        post("/leave") { call.respondWithHero(repo.leave(call.heroId)) }
        post("/kick") { call.respondWithHero(repo.kick(call.heroId, call.queryParam("memberId"))) }
        post("/promote") { call.respondWithHero(repo.promote(call.heroId, call.queryParam("memberId"))) }
        post("/demote") { call.respondWithHero(repo.demote(call.heroId, call.queryParam("memberId"))) }
        post("/transfer") { call.respondWithHero(repo.transfer(call.heroId, call.queryParam("memberId"))) }
        post("/disband") { call.respondWithHero(repo.disband(call.heroId)) }
        post("/settings") {
            call.respondWithHero(repo.settings(
                call.heroId, call.mode(), call.optionalParam("minLevel")?.let { it.toIntOrNull() ?: throw BaseRouteExceptions.funExceptionQuery("settings", "minLevel=$it") },
                call.optionalParam("emblem"), call.optionalParam("color"), call.announcement(),
            ))
        }
        post("/contribute") { call.respondWithHero(repo.contribute(call.heroId, call.queryParam("item"), call.queryParam("amount", 0L))) }
        get("/quests") { call.respondOk(repo.quests(call.heroId)) }
        post("/quests/claim") { call.respondWithHero(repo.claimQuest(call.heroId, call.optionalParam("questId"), call.optionalParam("goal"))) }
        get("/log") { call.respondOk(repo.log(call.heroId, call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT))) }
    }

    private fun ApplicationCall.mode(): GuildMode? = optionalParam("mode")?.let { raw ->
        GuildMode.entries.find { it.name.equals(raw, ignoreCase = true) } ?: throw BaseRouteExceptions.funExceptionQuery("mode", "mode=$raw")
    }

    /** Объявление главы: параметром (пустой - стереть) или телом `{announcement}`; нет ни того, ни другого - не менять. */
    private suspend fun ApplicationCall.announcement(): String? {
        request.queryParameters["announcement"]?.let { return it }
        val body = receiveText().takeIf { it.isNotBlank() } ?: return null
        return AppJson.decodeFromString(GuildSettingsBody.serializer(), body).announcement
    }
}
