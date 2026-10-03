package ru.descend.exileforge.features.data.guild

import com.sperance.exileforge.rules.content.GuildMode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.descend.exileforge.CONST_PAGE_SIZE_DEFAULT
import ru.descend.exileforge.base.exception.BaseRouteExceptions
import ru.descend.exileforge.base.route.BaseRoute
import ru.descend.exileforge.base.route.heroId
import ru.descend.exileforge.base.route.optionalParam
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.logic.guild.GuildQuestService
import ru.descend.exileforge.features.logic.guild.GuildService
import ru.descend.exileforge.features.logic.guild.GuildStashService
import ru.descend.exileforge.features.logic.guild.GuildTreeService
import ru.descend.exileforge.features.logic.hero.respondWithHero
import ru.descend.exileforge.server.addons.AppJson

/** Маршруты гильдии (`/api/v1/guild/...`): каждый от имени героя `heroId`, команды отвечают со снимком героя. */
class GuildRoute(
    repo: GuildRepository,
    private val guilds: GuildService,
    private val tree: GuildTreeService,
    private val stash: GuildStashService,
    private val quests: GuildQuestService,
) : BaseRoute<Guild>(
    repository = repo,
    entitySerializer = Guild.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/mine") { call.respondOk(guilds.mine(call.heroId)) }
        get("/search") {
            call.respondOk(guilds.search(call.heroId, call.optionalParam("text"), call.optionalParam("faction"), call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT)))
        }
        post("/create") {
            call.respondWithHero(
                guilds.create(
                    call.heroId,
                    call.queryParam("name"),
                    call.queryParam("tag"),
                    call.queryParam("faction"),
                    call.queryParam("emblem"),
                    call.queryParam("color"),
                    call.mode() ?: GuildMode.OPEN,
                    call.queryParam("minLevel", 1),
                ),
            )
        }
        post("/join") { call.respondWithHero(guilds.join(call.heroId, call.queryParam("guildId"))) }
        post("/apply") { call.respondWithHero(guilds.submitApplication(call.heroId, call.queryParam("guildId"))) }
        post("/applications/accept") { call.respondWithHero(guilds.acceptApplication(call.heroId, call.queryParam("applicantId"))) }
        post("/applications/decline") { call.respondWithHero(guilds.declineApplication(call.heroId, call.queryParam("applicantId"))) }
        post("/invite") { call.respondWithHero(guilds.invite(call.heroId, call.queryParam("name"))) }
        post("/invites/accept") { call.respondWithHero(guilds.acceptInvite(call.heroId, call.queryParam("guildId"))) }
        post("/invites/decline") { call.respondWithHero(guilds.declineInvite(call.heroId, call.queryParam("guildId"))) }
        post("/leave") { call.respondWithHero(guilds.leave(call.heroId)) }
        post("/kick") { call.respondWithHero(guilds.kick(call.heroId, call.queryParam("memberId"))) }
        post("/promote") { call.respondWithHero(guilds.promote(call.heroId, call.queryParam("memberId"))) }
        post("/demote") { call.respondWithHero(guilds.demote(call.heroId, call.queryParam("memberId"))) }
        post("/transfer") { call.respondWithHero(guilds.transfer(call.heroId, call.queryParam("memberId"))) }
        post("/disband") { call.respondWithHero(guilds.disband(call.heroId)) }
        post("/settings") {
            call.respondWithHero(
                guilds.settings(
                    call.heroId,
                    call.mode(),
                    call.optionalParam("minLevel")?.let { it.toIntOrNull() ?: throw BaseRouteExceptions.funExceptionQuery("settings", "minLevel=$it") },
                    call.optionalParam("emblem"),
                    call.optionalParam("color"),
                    call.announcement(),
                ),
            )
        }
        post("/contribute") { call.respondWithHero(tree.contribute(call.heroId, call.queryParam("item"), call.queryParam("amount", 0L))) }
        post("/tree/take") { call.respondWithHero(tree.takeNode(call.heroId, call.queryParam("node"))) }
        post("/tree/reset") { call.respondWithHero(tree.resetTree(call.heroId)) }
        get("/stash") { call.respondOk(stash.stash(call.heroId)) }
        post("/stash/deposit") {
            call.respondWithHero(stash.deposit(call.heroId, call.queryParam("tab", 0), call.optionalParam("itemId"), call.optionalParam("code"), call.queryParam("amount", 1L)))
        }
        post("/stash/take") { call.respondWithHero(stash.take(call.heroId, call.queryParam("entryId"))) }
        post("/stash/tab") { call.respondWithHero(stash.tabRank(call.heroId, call.queryParam("tab", 0), call.queryParam("minRank", 0))) }
        get("/quests") { call.respondOk(quests.quests(call.heroId)) }
        post("/quests/claim") { call.respondWithHero(quests.claimQuest(call.heroId, call.optionalParam("questId"), call.optionalParam("goal"))) }
        get("/log") { call.respondOk(guilds.log(call.heroId, call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT))) }
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
