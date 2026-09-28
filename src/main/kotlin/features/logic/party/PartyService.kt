package features.logic.party

import base.exception.model.PartyExceptions
import com.sperance.exileforge.rules.party.PartyFrame
import com.sperance.exileforge.rules.party.PartyLaunch
import com.sperance.exileforge.rules.party.PartyMember
import com.sperance.exileforge.rules.party.PartyReasons
import com.sperance.exileforge.rules.content.PartyRule
import com.sperance.exileforge.rules.party.PartyStatus
import com.sperance.exileforge.rules.party.PartyView
import com.sperance.exileforge.rules.party.PartyWire
import config.ContentStore
import extensions.printLog
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.campaign.CampaignService
import features.logic.hero.HeroLocks
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Лобби кооператива (1.23.0) - в памяти процесса: живёт, пока идёт заход, и не переживает перезапуск.
 *
 * Хост собирает лобби до входа в зону, гости входят по коду или из списка гильдии. На старте хост входит
 * в зону картой как обычно - размер партии множит жетоны, - а каждый гость получает заход по семени хоста
 * ([CampaignService.follow]). Дальше сервер только передаёт кадры сокета: хост шлёт гостям карту и бой,
 * гости хосту свои команды; добычу каждый пишет своим журналом, и проигрывает её сервер как всегда.
 *
 * Отвалившийся ждётся правила `party.reconnectSeconds`: гость потом выбывает, хост распускает лобби.
 * Состояние меняется под одним замком, а запросы к базе идут вне его: замок героя, взятый маршрутом,
 * и замок лобби не должны ждать друг друга.
 */
class PartyService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val campaign: CampaignService by inject()
    private val content: ContentStore by inject()
    private val rule: PartyRule get() = content.index.campaign.party
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val parties = HashMap<String, Party>()
    private val byHero = HashMap<String, String>()
    private val links = HashMap<String, Link>()
    private val timers = HashMap<String, Job>()

    private class Party(val code: String, val hostId: String, val zone: String, val mapItem: String?, val guildId: String?) {
        val members = LinkedHashMap<String, PartyMember>()
        var status = PartyStatus.LOBBY
        val launches = HashMap<String, PartyLaunch>()
    }

    /** Сокет участника: кадры уходят через очередь, чтобы медленный гость не держал хоста. */
    private class Link(val session: WebSocketSession) {
        val outgoing = Channel<String>(OUTGOING, BufferOverflow.DROP_OLDEST)
    }

    private fun Party.view() = PartyView(code, hostId, zone, mapItem, guildId, status,
        members.values.map { it.copy(online = it.heroId in links) }, rule.maxSize)

    private fun partyOf(heroId: String): Party? = byHero[heroId]?.let(parties::get)

    suspend fun current(heroId: String): PartyView? = mutex.withLock { partyOf(heroId)?.view() }

    /** Вход в заход, выданный участнику на старте: гость, переподключившись, берёт его отсюда. */
    suspend fun launch(heroId: String): PartyLaunch? = mutex.withLock { partyOf(heroId)?.launches?.get(heroId) }

    /** Лобби гильдии героя, ещё не вышедшие и не полные. */
    suspend fun guild(heroId: String): List<PartyView> {
        val guildId = heroes.requireHero(heroId, "guild").guild?.id ?: return emptyList()
        return mutex.withLock {
            parties.values.filter { it.guildId == guildId && it.status == PartyStatus.LOBBY && it.members.size < rule.maxSize && heroId !in it.members }.map { it.view() }
        }
    }

    /** Хост собирает лобби в [mapCode]: зона должна быть ему открыта, карта [mapItem] - его. */
    suspend fun create(heroId: String, mapCode: String, mapItem: String?): PartyView {
        val method = "create"
        val hero = heroes.requireHero(heroId, method)
        campaign.openZone(hero, mapCode, method)
        mapItem?.let { hero.requireItem(it, method) }
        val view = mutex.withLock {
            partyOf(heroId)?.let { throw PartyExceptions.funExceptionAlreadyIn(method, it.code) }
            val party = Party(newCode(), heroId, mapCode, mapItem, hero.guild?.id)
            party.members[heroId] = member(hero)
            parties[party.code] = party
            byHero[heroId] = party.code
            party.view()
        }
        printLog("Party ${view.code} created by $heroId in $mapCode")
        return view
    }

    /** Гость входит по коду: лобби ещё не вышло, в нём есть место, зона открыта и ему. */
    suspend fun join(heroId: String, code: String): PartyView {
        val method = "join"
        val hero = heroes.requireHero(heroId, method)
        val key = code.trim().uppercase()
        val zone = mutex.withLock { parties[key]?.zone } ?: throw PartyExceptions.funExceptionNotFound(method, key)
        campaign.openZone(hero, zone, method)
        val party = mutex.withLock {
            partyOf(heroId)?.let { throw PartyExceptions.funExceptionAlreadyIn(method, it.code) }
            val party = parties[key] ?: throw PartyExceptions.funExceptionNotFound(method, key)
            if (party.status != PartyStatus.LOBBY) throw PartyExceptions.funExceptionStarted(method, key)
            if (party.members.size >= rule.maxSize) throw PartyExceptions.funExceptionFull(method, rule.maxSize.toString())
            party.members[heroId] = member(hero)
            byHero[heroId] = key
            party
        }
        roster(party)
        return mutex.withLock { party.view() }
    }

    /** Уход из лобби: хост его распускает, гость выбывает - его заход в зоне хоста закрывается. */
    suspend fun leave(heroId: String) {
        val party = mutex.withLock { partyOf(heroId) } ?: return
        // Замок героя уже взят его же запросом: заход гостя закрывается без него
        if (party.hostId == heroId) disband(party, PartyReasons.DISBANDED) else drop(party, heroId, null, locked = true)
    }

    /** Хост выставляет гостя [memberId]. */
    suspend fun kick(heroId: String, memberId: String) {
        val party = mutex.withLock { partyOf(heroId) } ?: throw PartyExceptions.funExceptionNotIn("kick", heroId)
        if (party.hostId != heroId) throw PartyExceptions.funExceptionNotHost("kick", party.code)
        if (memberId == heroId || memberId !in party.members) return
        drop(party, memberId, PartyReasons.KICKED)
    }

    /**
     * Хост выходит в путь: входит в зону картой лобби с размером партии, каждый гость в сети получает заход по
     * его семени. Гость, которому войти не удалось, выбывает; ответ - вход хоста.
     */
    suspend fun start(heroId: String): PartyLaunch {
        val method = "start"
        val party = mutex.withLock {
            val party = partyOf(heroId) ?: throw PartyExceptions.funExceptionNotIn(method, heroId)
            if (party.hostId != heroId) throw PartyExceptions.funExceptionNotHost(method, party.code)
            if (party.status != PartyStatus.LOBBY) throw PartyExceptions.funExceptionStarted(method, party.code)
            if (party.members.size < 2) throw PartyExceptions.funExceptionAlone(method, party.code)
            party.members.keys.firstOrNull { it !in links }?.let { throw PartyExceptions.funExceptionOffline(method, party.members[it]?.name ?: it) }
            party.status = PartyStatus.RUNNING
            party
        }
        val guests = mutex.withLock { party.members.keys.filter { it != heroId } }
        val start = try { campaign.start(heroId, party.zone, party.mapItem, guests.size + 1) }
            catch (e: Exception) { mutex.withLock { party.status = PartyStatus.LOBBY }; throw e }
        val world = campaign.worldOf(heroes.requireHero(heroId, method))
        val size = guests.size + 1
        val host = PartyLaunch(party.code, heroId, start, world, size)
        val entered = guests.associateWith { guest ->
            runCatching { HeroLocks.withLock(guest) { campaign.follow(guest, heroId) } }
                .onFailure { printLog("Party ${party.code}: $guest could not follow: ${it.message}") }.getOrNull()
        }
        val (gone, sent) = mutex.withLock {
            party.launches[heroId] = host
            val gone = mutableListOf<String>()
            val sent = mutableListOf<Pair<String, PartyLaunch>>()
            entered.forEach { (guest, run) ->
                when {
                    guest !in party.members -> gone += guest
                    run == null -> { party.members.remove(guest); byHero.remove(guest); gone += guest }
                    else -> PartyLaunch(party.code, heroId, run, world, size).also { party.launches[guest] = it; sent += guest to it }
                }
            }
            gone to sent
        }
        gone.forEach { guest -> HeroLocks.withLock(guest) { campaign.abandon(guest) } }
        sent.forEach { (guest, launch) -> send(guest, PartyFrame.Launched(launch)) }
        roster(party)
        return host
    }

    // ==================== Сокет ====================

    /** Участник на связи: кадры от него разбираются, пока сокет открыт; закрылся - начинается ожидание. */
    suspend fun connect(heroId: String, session: WebSocketSession) {
        val link = Link(session)
        val (old, party) = mutex.withLock {
            timers.remove(heroId)?.cancel()
            links.put(heroId, link) to partyOf(heroId)
        }
        old?.outgoing?.close()
        val writer = scope.launch { for (text in link.outgoing) session.send(Frame.Text(text)) }
        try {
            party?.let { roster(it) }
            mutex.withLock { partyOf(heroId)?.launches?.get(heroId) }?.takeIf { party?.hostId != heroId }?.let { send(heroId, PartyFrame.Launched(it)) }
            for (frame in session.incoming) if (frame is Frame.Text) PartyWire.decode(frame.readText())?.let { relay(heroId, it) }
        } finally {
            writer.cancel()
            lost(heroId, link)
        }
    }

    /** Кадр участника: хост пишет гостю или всем, гость - хосту. Остальное сервер не слушает. */
    private suspend fun relay(heroId: String, frame: PartyFrame) {
        val send = frame as? PartyFrame.Send ?: return
        val targets = mutex.withLock {
            val party = partyOf(heroId) ?: return
            when {
                party.hostId != heroId -> listOf(party.hostId)
                send.to != null -> listOfNotNull(send.to?.takeIf { it in party.members && it != heroId })
                else -> party.members.keys.filter { it != heroId }
            }
        }
        val text = PartyWire.encode(PartyFrame.Relay(heroId, send.payload))
        mutex.withLock { targets.mapNotNull(links::get) }.forEach { it.outgoing.trySend(text) }
    }

    private suspend fun lost(heroId: String, link: Link) {
        val party = mutex.withLock {
            if (links[heroId] !== link) return
            links.remove(heroId)
            link.outgoing.close()
            val party = partyOf(heroId) ?: return
            timers[heroId] = scope.launch {
                delay(rule.reconnectSeconds * 1000L)
                val still = mutex.withLock { (heroId !in links && partyOf(heroId) === party).also { if (it) timers.remove(heroId) } }
                if (!still) return@launch
                if (party.hostId == heroId) disband(party, PartyReasons.HOST_LOST) else drop(party, heroId, PartyReasons.TIMEOUT)
            }
            party
        }
        roster(party)
    }

    // ==================== Состав ====================

    private suspend fun drop(party: Party, heroId: String, reason: String?, locked: Boolean = false) {
        val running = mutex.withLock {
            if (party.members.remove(heroId) == null) return
            byHero.remove(heroId)
            party.launches.remove(heroId)
            party.status == PartyStatus.RUNNING
        }
        reason?.let { send(heroId, PartyFrame.Closed(it)) }
        if (running) if (locked) campaign.abandon(heroId) else HeroLocks.withLock(heroId) { campaign.abandon(heroId) }
        roster(party)
    }

    /** Лобби распущено: гости узнают об этом сразу, а их заходы в зоне хоста закрываются, дав им дописать журнал. */
    private suspend fun disband(party: Party, reason: String) {
        val (guests, running) = mutex.withLock {
            if (parties.remove(party.code) == null) return
            party.members.keys.forEach { byHero.remove(it); timers.remove(it)?.cancel() }
            party.members.keys.filter { it != party.hostId } to (party.status == PartyStatus.RUNNING)
        }
        guests.forEach { send(it, PartyFrame.Closed(reason)) }
        printLog("Party ${party.code} disbanded: $reason")
        if (running) scope.launch {
            delay(FLUSH_GRACE)
            guests.forEach { guest -> runCatching { HeroLocks.withLock(guest) { campaign.abandon(guest) } } }
        }
    }

    private suspend fun roster(party: Party) {
        val (text, targets) = mutex.withLock { PartyWire.encode(PartyFrame.Roster(party.view())) to party.members.keys.mapNotNull(links::get) }
        targets.forEach { it.outgoing.trySend(text) }
    }

    private suspend fun send(heroId: String, frame: PartyFrame) {
        mutex.withLock { links[heroId] }?.outgoing?.trySend(PartyWire.encode(frame))
    }

    private fun member(hero: Hero) = PartyMember(hero._id, hero.name, hero.heroClass, hero.level)

    private fun newCode(): String {
        while (true) {
            val code = String(CharArray(CODE_LENGTH) { ALPHABET.random() })
            if (code !in parties) return code
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
        /** Без похожих букв и цифр: код читают вслух и переписывают с экрана. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val OUTGOING = 256
        /** Сколько распущенное лобби ждёт, прежде чем закрыть заходы гостей: журнал успевает уйти. */
        const val FLUSH_GRACE = 20_000L
    }
}
