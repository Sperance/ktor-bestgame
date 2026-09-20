package config

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

/**
 * Версии схемы коллекций, которые нельзя починить пересевом.
 *
 * Справочники сидер переписывает на каждом старте, поэтому изменение их схемы
 * лечится само. А данные игроков - инвентарь, прокачанное дерево, сами
 * персонажи - пересеять нельзя: документ старого формата драйвер просто
 * не прочитает и уронит сервер раньше, чем до него дойдут руки.
 *
 * Поэтому у таких коллекций есть версия. Как только формат меняется,
 * номер поднимается здесь, и коллекция чистится до того, как её кто-то прочтёт.
 *
 * Чистка, а не миграция: проект в активной разработке, и писать конвертер
 * под каждый промежуточный формат дороже, чем он стоит. Когда появятся живые
 * игроки, здесь появится и честная миграция.
 */
object SchemaMigrator {

    private const val STATE_COLLECTION = "SchemaVersion"

    /**
     * Коллекция и версия её формата.
     *
     * Поднимайте номер, когда меняете поля сущности несовместимо:
     * убрали поле, переименовали, сменили тип.
     */
    private val versions = mapOf(
        // 2: rarity и corrupted переехали на экземпляр предмета
        // 3: база предмета стала модификаторами, у Modifier появились конверсии
        "CharacterEquipment" to 3,
        // 2: Modifier получил values вместо value
        "CharacterSkillNode" to 2,
        // 2: убраны stockSkills и params, добавлен classId
        "Character" to 2,
    )

    /**
     * Приводит базу к текущей схеме.
     *
     * Вызывается до загрузки кэшей и до сидера, вне транзакции:
     * drop меняет каталог MongoDB и внутри открытой транзакции недопустим.
     */
    suspend fun migrate() {
        val database = MongoFactory.getDatabase()
        val state = database.getCollection<Document>(STATE_COLLECTION)

        versions.forEach { (collectionName, expected) ->
            val stored = state.find(Filters.eq("_id", collectionName))
                .firstOrNull()
                ?.getInteger("version")
                ?: 0

            if (stored >= expected) return@forEach

            database.getCollection<Document>(collectionName).drop()
            state.replaceOne(
                Filters.eq("_id", collectionName),
                Document("_id", collectionName).append("version", expected),
                ReplaceOptions().upsert(true)
            )

            printLog("  → [$collectionName] schema $stored → $expected, collection dropped")
        }
    }
}
