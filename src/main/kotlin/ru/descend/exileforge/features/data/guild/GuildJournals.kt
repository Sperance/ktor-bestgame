package ru.descend.exileforge.features.data.guild
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec

/** Журнал гильдий - коллекция `GuildEvent`; новые записи сверху. */
class GuildEventRepository : BaseRepository<GuildEvent>(GuildEvent::class) {
    override val indexes = listOf(IndexSpec.on("guildId", "at"))

    suspend fun page(guildId: String, page: Int, size: Int): List<GuildLogEntry> = findPaged(Filters.eq("guildId", guildId), page, size, Sorts.orderBy(Sorts.descending("at"), Sorts.descending("_id"))).items
        .map { GuildLogEntry(it.at, it.kind, it.heroName, it.value) }

    suspend fun deleteByGuild(guildId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("guildId", guildId))
    }
}
