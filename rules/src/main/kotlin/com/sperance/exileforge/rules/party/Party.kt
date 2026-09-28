package com.sperance.exileforge.rules.party

import com.sperance.exileforge.rules.roll.AbyssWindow
import com.sperance.exileforge.rules.roll.ChestWindow
import com.sperance.exileforge.rules.roll.CrystalWindow
import com.sperance.exileforge.rules.roll.VaalZone
import com.sperance.exileforge.rules.run.RunStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Лобби кооператива (1.23.0): собирается до входа, [RUNNING] - хост вошёл в зону со всеми. */
@Serializable
enum class PartyStatus { LOBBY, RUNNING }

/** Участник лобби: герой, в сети ли он сейчас. */
@Serializable
data class PartyMember(val heroId: String, val name: String, val heroClass: String, val level: Int, val online: Boolean = false)

/** Лобби как его видят участники: код для входа, хост, зона и карта, с которой он войдёт, гильдия, участники. */
@Serializable
data class PartyView(
    val code: String,
    val hostId: String,
    val zone: String,
    val mapItem: String? = null,
    val guildId: String? = null,
    val status: PartyStatus = PartyStatus.LOBBY,
    val members: List<PartyMember> = emptyList(),
    val maxSize: Int = 4,
) {
    val host: PartyMember? get() = members.firstOrNull { it.heroId == hostId }
    fun isHost(heroId: String) = heroId == hostId
}

/**
 * Мир хоста на входе: окна сундуков, кристаллов и расщелин его зоны, когда вернётся её страж, Ваал-зона
 * и открыта ли порча. По ним и семени гость строит ту же карту, что видит хост.
 */
@Serializable
data class PartyWorld(
    val chests: ChestWindow? = null,
    val crystals: CrystalWindow? = null,
    val abyss: AbyssWindow? = null,
    val bossAt: Long = 0,
    val corruptionOpened: Boolean = false,
    val vaalZone: VaalZone? = null,
)

/** Вход участника в заход хоста: его собственный заход (семя хоста, своя добыча) и мир хоста. */
@Serializable
data class PartyLaunch(val code: String, val hostId: String, val start: RunStart, val world: PartyWorld, val size: Int)

/**
 * Кадр сокета лобби. Сервер шлёт [Roster], [Launched], [Relay] и [Closed]; участник - [Send]: хост
 * адресует гостя по [Send.to] или всех, гость - всегда хоста. Что внутри [Send.payload], серверу не важно:
 * это договор клиентов (позиция хоста, бой, команды гостя).
 */
@Serializable
sealed interface PartyFrame {
    @Serializable @SerialName("roster") data class Roster(val party: PartyView) : PartyFrame
    @Serializable @SerialName("launched") data class Launched(val launch: PartyLaunch) : PartyFrame
    @Serializable @SerialName("relay") data class Relay(val from: String, val payload: JsonElement) : PartyFrame
    @Serializable @SerialName("closed") data class Closed(val reason: String) : PartyFrame
    @Serializable @SerialName("send") data class Send(val payload: JsonElement, val to: String? = null) : PartyFrame
}

object PartyReasons {
    const val DISBANDED = "disbanded"
    const val KICKED = "kicked"
    const val HOST_LOST = "host_lost"
    const val TIMEOUT = "timeout"
}

/** Кодировка кадров сокета лобби - одна у сервера и клиента. */
object PartyWire {
    val json: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        classDiscriminator = "type"
    }

    fun encode(frame: PartyFrame): String = json.encodeToString(PartyFrame.serializer(), frame)
    fun decode(text: String): PartyFrame? = runCatching { json.decodeFromString(PartyFrame.serializer(), text) }.getOrNull()
}
