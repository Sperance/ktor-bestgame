package config

import CONST_FIELD_UPDATED
import CONST_FIELD_VERSION
import SERVER_VERSION
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import extensions.printLog
import features.data.guild.Guild
import features.data.guild.GuildChat
import features.data.guild.GuildEvent
import features.data.hero.Hero
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.time.LocalDateTime
import java.util.Date

/**
 * Разовая очистка одних гильдий при старте (1.25.0): покровители с бонусами уступили фракциям без бонусов,
 * и прежние гильдии сносятся целиком. База остаётся: пропадают коллекции [Guild], [GuildEvent], [GuildChat]
 * (заявки и приглашения лежат внутри гильдии), а у героев - членство, откат повторного вступления, знаки гильдии
 * и журнал гильдейских заданий.
 * Метка [MARKER] в коллекции [DatabaseWipe.COLLECTION] - как у [DatabaseWipe].
 */
object GuildWipe {

    /** Метка очистки гильдий. Новая очистка - новая метка. */
    const val MARKER = "guild-wipe-1.25.0"

    /** Сносит гильдии, если метки ещё нет. Зовётся после [DatabaseWipe] и до индексов - они вернутся с [DatabaseSeeder]. */
    suspend fun runOnce() {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Guild wipe $MARKER")
        listOf(Guild::class, GuildEvent::class, GuildChat::class).forEach { database.getCollection(it.simpleName!!, Document::class.java).drop() }
        val heroes = database.getCollection(Hero::class.simpleName!!, Document::class.java).updateMany(
            Filters.or(Filters.ne("guild", null), Filters.ne("guildLeftAt", 0L), Filters.ne("guildMarks", 0L), Filters.ne("quests.guild", null)),
            Updates.combine(
                Updates.set("guild", null), Updates.set("guildLeftAt", 0L), Updates.set("guildMarks", 0L), Updates.set("quests.guild", null),
                Updates.inc(CONST_FIELD_VERSION, 1L), Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()),
            ),
        )
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → guilds dropped, ${heroes.modifiedCount} heroes released, marker $MARKER written")
    }
}
