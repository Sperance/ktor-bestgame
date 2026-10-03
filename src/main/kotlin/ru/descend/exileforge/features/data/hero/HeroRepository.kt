package ru.descend.exileforge.features.data.hero

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.GuildQuestLog
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import org.bson.BsonDocument
import org.bson.Document
import org.bson.conversions.Bson
import ru.descend.exileforge.CONST_FIELD_UPDATED
import ru.descend.exileforge.CONST_FIELD_VERSION
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.now

/**
 * Документ героя: чтение с починкой под контент, запись с заходом ([HeroRunStore]) и точечные выборки.
 * Создание и удаление с их последствиями для аккаунта, лотов и гильдии - в
 * [ru.descend.exileforge.features.logic.hero.HeroService].
 */
class HeroRepository(
    private val content: ContentStore,
    private val runs: HeroRunStore,
    private val stats: ru.descend.exileforge.features.data.heroStats.HeroStatsStore,
) : BaseRepository<Hero>(Hero::class) {
    private val index: ContentIndex get() = content.index

    override val indexes = listOf(IndexSpec.unique("idx_unique_name", "name"), IndexSpec.on("userId"))

    private companion object {
        const val OWNERS_MAX = 50_000
    }

    /**
     * Перед каждой записью героя его копии сверяются с контентом (1.1.0): пропавшие описания уходят,
     * закреплённые строки дороллены, волшебная и редкая доведены до дна редкости. Какой бы путь ни
     * принёс вещь - выдача администратора, старый документ, правка контента, витрина торговца, - пустой
     * она не ляжет.
     */
    override suspend fun settle(entity: Hero) {
        reconcile(entity)
        trackPeaks(entity)
    }

    /** Рекорды выводимых счётчиков летописи растут на каждой записи: откат узлов не опускает их ниже взятого. */
    private fun trackPeaks(hero: Hero) = Counter.record(hero.counters, hero.derived())

    /**
     * Сверка копий героя с контентом (1.30.0 - и на чтении): показанное клиенту равно тому, что ляжет
     * записью. True - копии изменились, и читающему их стоит записать, иначе следующее чтение докатит иначе.
     */
    fun reconcile(hero: Hero): Boolean {
        val factory = ItemFactory(index)
        val dice by lazy { Dice.system() }
        var changed = false
        (hero.items.asSequence() + hero.overflow.asSequence() + hero.merchant?.offers.orEmpty().asSequence().map { it.item })
            .forEach { item -> index.template(item.template)?.let { if (factory.reconcile(it, item, dice)) changed = true } }
        forgetUnknownAtlas(hero)
        return changed
    }

    /** Узлы атласа, которых контент больше не знает, уходят (1.30.0): они не держат возврат и не едят очко. */
    private fun forgetUnknownAtlas(hero: Hero): Hero = hero.also { it.atlas.retainAll { code -> index.atlasGraph.node(code) != null } }

    /** Снятые предметы сумки (1.65.0) становятся своей заменой из правил: в сумке не остаётся кодов, которых нет в контенте. */
    private fun retireItems(hero: Hero): Hero = hero.also {
        index.rules.retired.forEach { (old, new) -> hero.bag.remove(old)?.let { amount -> hero.bag.merge(new, amount, Long::plus) } }
    }

    /** Заход героя живёт в [HeroRunStore]: в документе героя его нет. */
    override fun stored(document: BsonDocument) {
        (document["campaign"] as? BsonDocument)?.remove("run")
    }

    override suspend fun validateAfterUpdate(entity: Hero, session: ClientSession) {
        super.validateAfterUpdate(entity, session)
        if (runs.dirty(entity)) runs.write(entity, session)
    }

    override suspend fun validateAfterDelete(entity: Hero, session: ClientSession) {
        runs.delete(entity._id, session)
    }

    /** Герои одного аккаунта - то, из чего он выбирает при входе; их не больше трёх, страниц нет. */
    suspend fun findByUser(userId: String): List<Hero> = findByFilter(Filters.eq("userId", userId))

    /** Владелец героя - одно поле по `_id`: доступ спрашивает его на каждой команде, а владелец не меняется, так что ответ помнится (1.53.0). */
    suspend fun ownerOf(heroId: String): String? = owners.get(heroId) ?: collection.withDocumentClass<Document>().find(readFilter(Filters.eq("_id", heroId)))
        .projection(Projections.include("userId")).limit(1).firstOrNull()?.getString("userId")?.also { owners.put(heroId, it) }

    private val owners = ru.descend.exileforge.base.cache.BoundedCache<String, String>(OWNERS_MAX)

    /** Имя, класс и уровень героев [ids] - три поля без тайника: для состава и заявок гильдии. */
    suspend fun cards(ids: Collection<String>): Map<String, HeroCard> {
        if (ids.isEmpty()) return emptyMap()
        return collection.withDocumentClass<Document>().find(readFilter(Filters.`in`("_id", ids)))
            .projection(Projections.include("name", "heroClass", "level")).toList()
            .associate { doc -> doc.getString("_id") to HeroCard(doc.getString("_id"), doc.getString("name").orEmpty(), doc.getString("heroClass").orEmpty(), doc.getInteger("level", 1)) }
    }

    /** Гильдейские счётчики заданий героев (1.21.0) - только они, без документов целиком. */
    suspend fun guildTallies(ids: Collection<String>): Map<String, GuildQuestLog> {
        if (ids.isEmpty()) return emptyMap()
        fun counts(doc: Document?): MutableMap<String, Long> = doc?.entries?.associateTo(HashMap()) { (key, value) -> key to ((value as? Number)?.toLong() ?: 0L) } ?: mutableMapOf()
        return collection.withDocumentClass<Document>().find(readFilter(Filters.`in`("_id", ids)))
            .projection(Projections.include("quests.guild")).toList()
            .mapNotNull { doc ->
                val guild = (doc["quests"] as? Document)?.get("guild") as? Document ?: return@mapNotNull null
                val id = guild.getString("id") ?: return@mapNotNull null
                doc.getString("_id") to GuildQuestLog(
                    id,
                    (guild["day"] as? Number)?.toLong() ?: 0,
                    (guild["week"] as? Number)?.toLong() ?: 0,
                    counts(guild["dayCounts"] as? Document),
                    counts(guild["weekCounts"] as? Document),
                )
            }.toMap()
    }

    /**
     * Правка гильдии у нескольких героев разом, мимо объектов в памяти: версия каждого растёт, так что
     * копия, прочитанная до правки, запишется только гонкой и перечитается.
     */
    suspend fun patchGuild(filter: Bson, update: Bson, session: ClientSession) {
        collection.updateMany(session, readFilter(filter), Updates.combine(update, Updates.inc(CONST_FIELD_VERSION, 1L), Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now())))
    }

    /** Герой для команды: с заходом ([HeroRunStore.hydrate]), без забытых узлов атласа и снятых предметов сумки. */
    suspend fun requireHero(heroId: String, method: String): Hero = runs.hydrate(retireItems(forgetUnknownAtlas(requireById(heroId) { CharacterExceptions.funExceptionNotFound(method, it) })))

    /** Одна запись героя без транзакции (1.53.0): один документ с фильтром по версии атомарен сам, объект в памяти идёт в ногу с базой. */
    suspend fun save(hero: Hero, method: String): Hero {
        // Изменившийся заход пишется в одной транзакции с героем ([validateAfterUpdate]); иначе - одна запись без неё
        if (runs.dirty(hero)) transactionExecute(method) { update(hero, it) } else replace(hero, method)
        stats.bump(hero._id, hero.stats)
        return hero
    }
}

/** Герой в составе гильдии: только то, что видно в списке. */
data class HeroCard(val id: String, val name: String, val heroClass: String, val level: Int)
