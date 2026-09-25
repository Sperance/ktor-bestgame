package features.logic.world

import features.caches.CharacterClassCache
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import features.caches.SkillTreeCache
import features.data.equipment.equipment_data.Equipment
import features.data.items.Items
import features.logic.modifiers.ModifierDefinition
import features.logic.progression.CharacterClass
import features.logic.progression.ExperienceLevel
import features.logic.skilltree.SkillTreeNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import server.addons.AppJson
import server.addons.StatTables
import java.security.MessageDigest

/**
 * Манифест справочников внутри `static/index.json`: отпечаток и имя файла, как у иконок.
 */
@Serializable
data class WorldManifest(val hash: String, val file: String)

/**
 * Все справочники мира одним файлом (с 0.48.0): модификаторы, классы, уровни, дерево,
 * шаблоны экипировки, предметы и таблицы листа. С 0.54.0 у каждого модификатора его тиры -
 * номер и диапазоны значений: по ним клиент показывает, насколько хорош ролл внутри тира.
 *
 * Клиент раньше читал их девятью запросами в каждой сессии. Теперь он хранит файл у себя и
 * качает заново, только когда разошёлся [WorldManifest.hash]. Файл собирается из тех же кешей,
 * что обслуживают сервер, поэтому правка администратора сразу меняет отпечаток: кеши считают
 * свои правки ([features.caches.MongoCache.revision]), и сборка повторяется, лишь когда сумма
 * этих счётчиков сдвинулась.
 */
object WorldBundle : KoinComponent {
    const val FOLDER = "world"
    const val FILE = "world.json"

    private val modifiers: ModifierDefinitionCache by inject()
    private val tiers: ModifierTierCache by inject()
    private val classes: CharacterClassCache by inject()
    private val levels: ExperienceLevelCache by inject()
    private val tree: SkillTreeCache by inject()
    private val equipment: EquipmentCache by inject()
    private val items: ItemsCache by inject()

    private val compact = Json(AppJson) { prettyPrint = false }

    private class Built(val revision: Long, val document: String, val hash: String)

    @Volatile private var built: Built? = null

    fun manifest(): WorldManifest = WorldManifest(current().hash, FILE)

    fun document(): String = current().document

    fun hash(): String = current().hash

    private fun revision(): Long =
        modifiers.revision + tiers.revision + classes.revision + levels.revision + tree.revision + equipment.revision + items.revision

    @Synchronized
    private fun current(): Built {
        val revision = revision()
        built?.takeIf { it.revision == revision }?.let { return it }
        val document = compact.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject {
            put("modifiers", withTiers(encode(ModifierDefinition.serializer(), modifiers.getCache().toList())))
            put("classes", encode(CharacterClass.serializer(), classes.getCache().toList()))
            put("levels", encode(ExperienceLevel.serializer(), levels.getCache().toList()))
            put("tree", encode(SkillTreeNode.serializer(), tree.getCache().toList()))
            put("equipment", encode(Equipment.serializer(), equipment.getCache().toList()))
            put("items", encode(Items.serializer(), items.getCache().toList()))
            put("stats", AppJson.encodeToJsonElement(StatTables.serializer(), StatTables.served))
        })
        return Built(revision, document, sha256(document)).also { built = it }
    }

    /** Каждому описанию - его тиры: `{"tier": 1, "values": [[min, max], ...]}`, от лучшего к худшему. */
    private fun withTiers(definitions: kotlinx.serialization.json.JsonElement) = JsonArray((definitions as JsonArray).map { row ->
        val definition = row as JsonObject
        val id = (definition["_id"] as? JsonPrimitive)?.content.orEmpty()
        JsonObject(definition + ("tiers" to JsonArray(tiers.findByModifier(id).map { tier ->
            JsonObject(mapOf(
                "tier" to JsonPrimitive(tier.tier),
                "values" to JsonArray(tier.values.map { JsonArray(listOf(JsonPrimitive(it.valueMin), JsonPrimitive(it.valueMax))) }),
            ))
        })))
    })

    /** По `_id`: порядок кеша сдвигается при правке, а отпечаток от порядка зависеть не должен. */
    private fun <T : base.entity.StockEntity> encode(serializer: KSerializer<T>, rows: List<T>) =
        AppJson.encodeToJsonElement(ListSerializer(serializer), rows.sortedBy { it._id })

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
