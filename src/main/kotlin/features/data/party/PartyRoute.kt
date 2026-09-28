package features.data.party

import base.route.RouteRegistrar
import base.route.apiPath
import base.route.heroId
import base.route.mapCode
import base.route.optionalParam
import base.route.queryParam
import base.route.respondOk
import extensions.saveChildren
import features.logic.hero.PARTY_SOCKET
import features.logic.hero.respondWithHero
import features.logic.party.PartyService
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket

/**
 * Кооператив (`/api/v1/party/...`, 1.23.0): лобби по коду или из гильдии, старт хостом и сокет, по которому
 * хост и гости обмениваются кадрами захода. Каждый маршрут - от имени героя `heroId`, его принадлежность
 * проверил доступ; сокет в очередь героя не встаёт.
 */
class PartyRoute(private val parties: PartyService) : RouteRegistrar {
    override fun register(routing: Routing) {
        routing.route(apiPath("party")) {
            get { call.respondOk(parties.current(call.heroId)) }
            get("/guild") { call.respondOk(parties.guild(call.heroId)) }
            get("/launch") { call.respondOk(parties.launch(call.heroId)) }
            post("/create") { call.respondOk(parties.create(call.heroId, call.mapCode, call.optionalParam("itemId"))) }
            post("/join") { call.respondOk(parties.join(call.heroId, call.queryParam("code"))) }
            post("/leave") { call.respondOk(parties.leave(call.heroId).let { true }) }
            post("/kick") { call.respondOk(parties.kick(call.heroId, call.queryParam("memberId")).let { true }) }
            post("/start") { call.respondWithHero(parties.start(call.heroId)) }
            webSocket(PARTY_SOCKET.removePrefix("/party")) { parties.connect(call.heroId, this) }
        }.saveChildren()
    }
}
