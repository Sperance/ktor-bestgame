package features.logic.hero

import base.exception.model.CharacterExceptions
import base.route.ApiMongoResponse
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.logic.bench.BenchRecipe
import features.logic.crafts.CraftsService
import features.logic.skilltree.CharacterSkillTreeState
import extensions.printLog
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import server.addons.AppJson
import java.security.MessageDigest

/**
 * Одна часть героя и её отпечаток.
 *
 * Клиент заменяет часть целиком и никогда не сливает её по элементам: отпечаток говорит лишь,
 * та это часть или другая.
 */
@Serializable
data class HeroPart(val version: String, val data: JsonElement)

/**
 * Снимок героя (с 0.48.0): персонаж, его вещи, дерево, сумка и верстак.
 *
 * [version] - отпечаток всего снимка, он же `ETag` у `GET /character/view`. В [parts] лежат
 * только те части, чьих отпечатков клиент не прислал в [HEADER]: остальные у него уже есть.
 */
@Serializable
data class HeroSnapshot(val version: String, val parts: Map<String, HeroPart>)

/**
 * Снимки героя для ответов команд и для `GET /character/view`.
 *
 * До 0.48.0 клиент после каждой команды перечитывал героя четырьмя запросами. Теперь команда
 * отвечает снимком сама, а холодное чтение - один запрос, чаще всего с ответом 304.
 */
object HeroSnapshots : KoinComponent {
    /** Заголовок, в котором клиент перечисляет свои части: `character=<hash>,inventory=<hash>`. */
    const val HEADER = "X-Hero-Parts"

    const val CHARACTER = "character"
    const val INVENTORY = "inventory"
    const val TREE = "tree"
    const val BAG = "bag"
    const val BENCH = "bench"

    private val characters: CharacterRepository by inject()
    private val equipment: CharacterEquipmentRepository by inject()
    private val crafts: CraftsService by inject()

    private val compact = Json(AppJson) { prettyPrint = false }

    /** Части, которые клиент назвал в [HEADER]; битый заголовок значит «ничего нет». */
    fun known(header: String?): Map<String, String> =
        header.orEmpty().split(',').mapNotNull { pair ->
            val name = pair.substringBefore('=', "").trim()
            val hash = pair.substringAfter('=', "").trim()
            if (name.isEmpty() || hash.isEmpty()) null else name to hash
        }.toMap()

    /**
     * Снимок героя сейчас.
     *
     * Работа ремесла досчитывается первой, как при чтении сумки: иначе снимок показал бы сумку
     * без того, что уже добыто.
     */
    suspend fun of(characterId: String, known: Map<String, String> = emptyMap()): HeroSnapshot {
        crafts.settle(characterId)
        val character = characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound("heroView", characterId)
        val all = linkedMapOf(
            CHARACTER to part(Character.serializer(), character),
            INVENTORY to part(ListSerializer(CharacterEquipment.serializer()), equipment.findByCharacter(characterId)),
            TREE to part(CharacterSkillTreeState.serializer(), characters.skillTreeState(characterId)),
            BAG to part(ListSerializer(CharacterItems.serializer()), character.parseItems()),
            BENCH to part(ListSerializer(BenchRecipe.serializer()), equipment.bench(characterId)),
        )
        val version = sha256(all.entries.joinToString(",") { "${it.key}=${it.value.version}" })
        return HeroSnapshot(version, all.filter { (name, part) -> known[name] != part.version })
    }

    /**
     * Снимок для ответа команды. Команда уже прошла, поэтому сбой снимка её не отменяет: ответ
     * уходит без снимка, и клиент перечитает героя сам.
     */
    suspend fun afterCommand(characterId: String?, header: String?): HeroSnapshot? {
        if (characterId.isNullOrBlank()) return null
        return try { of(characterId, known(header)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { printLog("[HeroSnapshots] $characterId: ${e.message}"); null }
    }

    /**
     * Снимок для ответа команды, если клиент его просил: заголовок [HEADER] есть, пусть и пустой
     * (`none`). Старый клиент его не шлёт и снимка не получает.
     */
    suspend fun forCall(call: ApplicationCall): HeroSnapshot? {
        val header = call.request.headers[HEADER] ?: return null
        return afterCommand(call.request.queryParameters["characterId"], header)
    }

    private fun <T> part(serializer: KSerializer<T>, value: T): HeroPart {
        val element = AppJson.encodeToJsonElement(serializer, value)
        return HeroPart(sha256(compact.encodeToString(JsonElement.serializer(), element)).take(16), element)
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Ответ команды героя: данные как раньше и рядом снимок героя после неё. */
suspend inline fun <reified T> ApplicationCall.respondWithHero(data: T) =
    respond(ApiMongoResponse(success = true, data = data, hero = HeroSnapshots.forCall(this)))
