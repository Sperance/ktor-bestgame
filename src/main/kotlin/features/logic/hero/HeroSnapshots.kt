package features.logic.hero

import base.exception.model.CharacterExceptions
import base.route.ApiMongoResponse
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ModifierDefinitionCache
import features.caches.SkillTreeCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.toCharacterItems
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import server.addons.AppJson

/**
 * Одна часть героя и её версия.
 *
 * Часть целиком - когда [base] пуст: клиент заменяет её и ничего не сливает. Инвентарь
 * (с 0.49.0) может прийти дельтой: [base] называет версию, поверх которой она ложится, а
 * [data] - это [InventoryPatch]. Дельта уходит только тому, кто держит ровно [base].
 */
@Serializable
data class HeroPart(val version: String, val data: JsonElement, val base: String? = null)

/** Что изменилось в инвентаре за команду: вещи, которые встали или сменились, и id ушедших. */
@Serializable
data class InventoryPatch(val changed: List<CharacterEquipment>, val removed: List<String>)

/**
 * Снимок героя (с 0.48.0): персонаж, его вещи, дерево, сумка и верстак.
 * [version] - версия всего снимка, она же `ETag` у `GET /character/view`. В [parts] лежат
 * только те части, чьих версий клиент не прислал в [HeroSnapshots.HEADER]: остальные у него уже есть.
 */
@Serializable
data class HeroSnapshot(val version: String, val parts: Map<String, HeroPart>)

/**
 * Снимки героя для ответов команд и для `GET /character/view`.
 *
 * До 0.49.0 снимок перечитывал персонажа несколько раз, кодировал и хешировал каждую часть,
 * даже ту, что у клиента уже есть. Теперь версии частей - счётчики: `version` персонажа
 * (он же у сумки, верстака и дерева), `inventoryRevision` у инвентаря плюс ревизии
 * справочников, от которых часть зависит. Совпал счётчик - часть не кодируется вовсе;
 * персонаж читается один раз, инвентарь - только если он у клиента устарел, и чаще всего
 * уходит дельтой из журнала запроса [HeroChanges].
 */
object HeroSnapshots : KoinComponent {
    /** Заголовок, в котором клиент перечисляет свои части: `character=<version>,inventory=<version>`. */
    const val HEADER = "X-Hero-Parts"
    const val CHARACTER = "character"
    const val INVENTORY = "inventory"
    const val TREE = "tree"
    const val BAG = "bag"
    const val BENCH = "bench"

    private val characters: CharacterRepository by inject()
    private val equipment: CharacterEquipmentRepository by inject()
    private val crafts: CraftsService by inject()
    private val tree: SkillTreeCache by inject()
    private val levels: ExperienceLevelCache by inject()
    private val modifiers: ModifierDefinitionCache by inject()
    private val templates: EquipmentCache by inject()

    /** Части, которые клиент назвал в [HEADER]; битый заголовок значит «ничего нет». */
    fun known(header: String?): Map<String, String> =
        header.orEmpty().split(',').mapNotNull { pair ->
            val name = pair.substringBefore('=', "").trim()
            val hash = pair.substringAfter('=', "").trim()
            if (name.isEmpty() || hash.isEmpty()) null else name to hash
        }.toMap()

    /**
     * Снимок героя сейчас.
     * Работа ремесла досчитывается первой, как при чтении сумки: иначе снимок показал бы сумку
     * без того, что уже добыто.
     */
    suspend fun of(characterId: String, known: Map<String, String> = emptyMap()): HeroSnapshot {
        val character = characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound("heroView", characterId)
        crafts.settle(character)
        val changes = heroChanges()
        val bump = changes?.bumpOf(characterId)
        val inventoryRevision = bump?.after ?: character.inventoryRevision
        val worldRevision = tree.revision + levels.revision + modifiers.revision + templates.revision

        val characterVersion = character.version.toString()
        val inventoryVersion = "$inventoryRevision.${templates.revision}"
        val treeVersion = "${character.version}.$worldRevision"
        val parts = LinkedHashMap<String, HeroPart>()

        if (known[CHARACTER] != characterVersion) parts[CHARACTER] = part(characterVersion, Character.serializer(), character)
        if (known[INVENTORY] != inventoryVersion) parts[INVENTORY] = inventoryPart(character, inventoryVersion, known[INVENTORY], bump, changes)
        if (known[TREE] != treeVersion) parts[TREE] = part(treeVersion, CharacterSkillTreeState.serializer(), characters.skillTreeState(character))
        if (known[BAG] != characterVersion) parts[BAG] = part(characterVersion, ListSerializer(CharacterItems.serializer()), character.bag.toCharacterItems())
        if (known[BENCH] != characterVersion) parts[BENCH] = part(characterVersion, ListSerializer(BenchRecipe.serializer()), equipment.bench(character))

        return HeroSnapshot("$characterVersion.$inventoryVersion.$worldRevision", parts)
    }

    /**
     * Инвентарь дельтой, если клиент держит ровно ту версию, с которой этот запрос начал, и
     * ревизия выросла только его записями; иначе списком целиком.
     */
    private suspend fun inventoryPart(character: Character, version: String, knownVersion: String?, bump: HeroChanges.Bump?, changes: HeroChanges?): HeroPart {
        val delta = changes?.deltaFor(character._id)
        if (bump != null && delta != null && knownVersion == "${bump.before}.${templates.revision}" && bump.after == bump.before + bump.count) {
            return HeroPart(version, AppJson.encodeToJsonElement(InventoryPatch.serializer(), InventoryPatch(delta.changed, delta.removed)), base = knownVersion)
        }
        return part(version, ListSerializer(CharacterEquipment.serializer()), equipment.findByCharacter(character._id))
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

    private fun <T> part(version: String, serializer: KSerializer<T>, value: T): HeroPart =
        HeroPart(version, AppJson.encodeToJsonElement(serializer, value))
}

/** Ответ команды героя: данные как раньше и рядом снимок героя после неё. */
suspend inline fun <reified T> ApplicationCall.respondWithHero(data: T) =
    respond(ApiMongoResponse(success = true, data = data, hero = HeroSnapshots.forCall(this)))
