package features.data.guild

import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession

/** Журнал гильдий - коллекция `GuildEvent`; новые записи сверху. */
class GuildEventRepository : BaseRepository<GuildEvent>(GuildEvent::class) {
    override val indexes = listOf(IndexSpec.on("guildId", "at"))

    suspend fun page(guildId: String, page: Int, size: Int): List<GuildLogEntry> = findPaged(Filters.eq("guildId", guildId), page, size, Sorts.orderBy(Sorts.descending("at"), Sorts.descending("_id"))).items
        .map { GuildLogEntry(it.at, it.kind, it.heroName, it.value) }

    suspend fun deleteByGuild(guildId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("guildId", guildId))
    }
}
