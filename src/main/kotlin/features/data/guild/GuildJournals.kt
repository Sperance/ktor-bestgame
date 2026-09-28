package features.data.guild

import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/** Журнал гильдий - коллекция `GuildEvent`; новые записи сверху. */
class GuildEventRepository : BaseRepository<GuildEvent>(GuildEvent::class) {
    override val indexes = listOf(IndexSpec.on("guildId", "at"))

    suspend fun page(guildId: String, page: Int, size: Int): List<GuildLogEntry> =
        findPaged(Filters.eq("guildId", guildId), page, size, Sorts.orderBy(Sorts.descending("at"), Sorts.descending("_id"))).items
            .map { GuildLogEntry(it.at, it.kind, it.heroName, it.value) }

    suspend fun deleteByGuild(guildId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("guildId", guildId))
    }
}

/** Чат гильдий - коллекция `GuildChat`: у гильдии живут только последние `chat.keep` сообщений. */
class GuildChatRepository : BaseRepository<GuildChat>(GuildChat::class) {
    override val indexes = listOf(IndexSpec.on("guildId", "at"), IndexSpec.on("guildId", "heroId", "at"))

    /** Сообщения позже [after] (мс эпохи) по порядку, не больше [limit]. */
    suspend fun after(guildId: String, after: Long, limit: Int): List<GuildChat> =
        collection.find(readFilter(Filters.and(Filters.eq("guildId", guildId), Filters.gt("at", after))))
            .sort(Sorts.orderBy(Sorts.ascending("at"), Sorts.ascending("_id"))).limit(limit).toList()

    /** Когда герой последний раз писал в чат гильдии; null - не писал. */
    suspend fun lastAt(guildId: String, heroId: String): Long? =
        collection.find(readFilter(Filters.and(Filters.eq("guildId", guildId), Filters.eq("heroId", heroId))))
            .sort(Sorts.descending("at")).limit(1).firstOrNull()?.at

    /** Новое сообщение и срез хвоста: всё старше [keep] последних уходит той же транзакцией. */
    suspend fun post(message: GuildChat, keep: Int, session: ClientSession): GuildChat {
        insert(message, session)
        val edge = collection.find(session, Filters.eq("guildId", message.guildId))
            .sort(Sorts.orderBy(Sorts.descending("at"), Sorts.descending("_id"))).skip(keep).limit(1).firstOrNull() ?: return message
        collection.deleteMany(session, Filters.and(Filters.eq("guildId", message.guildId), Filters.lte("at", edge.at), Filters.ne("_id", message._id)))
        return message
    }

    suspend fun deleteByGuild(guildId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("guildId", guildId))
    }
}
