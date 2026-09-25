package features.logic.world

import features.caches.CharacterClassCache
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.PoolCache
import features.caches.SkillTreeCache
import features.data.equipment.equipment_data.Equipment
import features.data.items.Items
import features.logic.modifiers.ModifierDefinition
import features.logic.pools.Pool
import features.logic.progression.CharacterClass
import features.logic.progression.ExperienceLevel
import features.logic.skilltree.SkillTreeNode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * шаблоны экипировки, предметы, пулы и таблицы листа. У каждого модификатора его тиры
 * (`tiers`, с 0.56.0 - прямо в описании: уровень и диапазоны значений): по ним клиент
 * показывает, насколько хорош ролл внутри тира. Пулы (0.56.0) - документы коллекции `Pool`.
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
    private val pools: PoolCache by inject()
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
        modifiers.revision + pools.revision + classes.revision + levels.revision + tree.revision + equipment.revision + items.revision

    @Synchronized
    private fun current(): Built {
        val revision = revision()
        built?.takeIf { it.revision == revision }?.let { return it }
        val document = compact.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject {
            put("modifiers", encode(ModifierDefinition.serializer(), modifiers.getCache().toList()))
            put("classes", encode(CharacterClass.serializer(), classes.getCache().toList()))
            put("levels", encode(ExperienceLevel.serializer(), levels.getCache().toList()))
            put("tree", encode(SkillTreeNode.serializer(), tree.getCache().toList()))
            put("equipment", encode(Equipment.serializer(), equipment.getCache().toList()))
            put("items", encode(Items.serializer(), items.getCache().toList()))
            put("pools", encode(Pool.serializer(), pools.getCache().toList()))
            put("stats", AppJson.encodeToJsonElement(StatTables.serializer(), StatTables.served))
        })
        return Built(revision, document, sha256(document)).also { built = it }
    }

    /** По `_id`: порядок кеша сдвигается при правке, а отпечаток от порядка зависеть не должен. */
    private fun <T : base.entity.StockEntity> encode(serializer: KSerializer<T>, rows: List<T>) =
        AppJson.encodeToJsonElement(ListSerializer(serializer), rows.sortedBy { it._id })

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
