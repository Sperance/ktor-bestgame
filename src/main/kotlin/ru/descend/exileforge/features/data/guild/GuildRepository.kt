package ru.descend.exileforge.features.data.guild
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.base.route.PagedMongoResponse
import ru.descend.exileforge.config.ContentStore
import java.util.regex.Pattern

/**
 * Документы гильдий (1.20.0): индексы, выборки по составу, заявкам и приглашениям, починка казны.
 * Правда о составе - документ гильдии; у героя лежит копия [ru.descend.exileforge.features.data.hero.HeroGuild].
 * Логика гильдии - в `features/logic/guild`.
 */
class GuildRepository(private val content: ContentStore) : BaseRepository<Guild>(Guild::class) {
    private val index: ContentIndex get() = content.index

    override val indexes = listOf(
        IndexSpec.unique("idx_unique_guild_name", "nameKey"),
        IndexSpec.unique("idx_unique_guild_tag", "tag"),
        IndexSpec.on("members.heroId"),
        IndexSpec.on("applications.heroId"),
        IndexSpec.on("invites.heroId"),
    )

    /** Гильдия, в составе которой герой [heroId]. */
    suspend fun byMember(heroId: String): Guild? = findByFilter(Filters.eq("members.heroId", heroId)).firstOrNull()?.let(::retireOrbs)

    /** Гильдия героя [heroId] в транзакции [session] - при удалении героя. */
    suspend fun byMember(heroId: String, session: ClientSession): Guild? = collection.find(session, readFilter(Filters.eq("members.heroId", heroId))).firstOrNull()

    /** Гильдия [guildId] с починкой казны. */
    suspend fun find(guildId: String): Guild? = findById(guildId)?.let(::retireOrbs)

    /** Гильдии, пригласившие героя [heroId]. */
    suspend fun inviting(heroId: String): List<Guild> = findByFilter(Filters.eq("invites.heroId", heroId))

    /** Поиск по имени или тегу [text]; [faction] - только гильдии этой фракции. Порядок - по опыту (рейтинг). */
    suspend fun search(text: String?, faction: String?, page: Int, size: Int): PagedMongoResponse<Guild> {
        val filters = listOfNotNull(
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { Filters.or(Filters.regex("name", Pattern.quote(it), "i"), Filters.eq("tag", it.uppercase())) },
            faction?.trim()?.takeIf { it.isNotEmpty() }?.let { Filters.eq("faction", it) },
        )
        val filter = if (filters.isEmpty()) Filters.empty() else Filters.and(filters)
        return findPaged(filter, page, size, Sorts.orderBy(Sorts.descending("experience"), Sorts.ascending("_id")))
    }

    /** Заявки и приглашения героя во все гильдии, кроме [except], снимаются. */
    suspend fun forgetPending(heroId: String, except: String?, session: ClientSession) {
        val pending = Filters.or(Filters.eq("applications.heroId", heroId), Filters.eq("invites.heroId", heroId))
        val filter = except?.let { Filters.and(pending, Filters.ne("_id", it)) } ?: pending
        collection.updateMany(
            session,
            readFilter(filter),
            Updates.combine(Updates.pull("applications", Document("heroId", heroId)), Updates.pull("invites", Document("heroId", heroId)), Updates.inc("version", 1L)),
        )
    }

    /** Снятые сферы казны становятся своей заменой из правил - как в сумке героя: в казне не остаётся кодов, которых нет в контенте. */
    private fun retireOrbs(guild: Guild): Guild = guild.also {
        index.rules.retired.forEach { (old, new) -> guild.treasuryOrbs.remove(old)?.let { amount -> guild.treasuryOrbs.merge(new, amount, Long::plus) } }
    }
}
