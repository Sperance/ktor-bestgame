package ru.descend.exileforge.features.data.hero
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import ru.descend.exileforge.config.MongoFactory
import ru.descend.exileforge.server.addons.AppJson

/** Открытый заход героя в своей коллекции (1.63.0): `_id` - id героя. */
@Serializable
data class HeroRun(val _id: String, val run: RunState)

/**
 * Заход героя отдельно от документа героя (1.63.0). Журнал заходов пишется часто и растёт (убийства, итог), а герою он
 * нужен только в кампании: в документе героя его больше нет ([HeroRepository.stored]). Репозиторий героя подкладывает
 * заход в `hero.campaign.run` при чтении ([hydrate]) и пишет его в той же транзакции, что героя ([write]) - только если
 * он изменился. Заход, оставшийся в документе героя от прежних версий, переносится при первом чтении.
 */
object HeroRunStore {
    private val collection: MongoCollection<HeroRun> by lazy { MongoFactory.getDatabase().getCollection("HeroRun", HeroRun::class.java) }

    /** Коллекция создаётся при старте: в транзакции её неявное создание не везде разрешено. */
    suspend fun ensureCollection() {
        runCatching { MongoFactory.getDatabase().createCollection("HeroRun") }
    }

    /** Отпечаток захода для сравнения при записи. */
    fun print(run: RunState?): String = AppJson.encodeToString(RunState.serializer().nullable, run)

    /**
     * Подкладывает заход героя из коллекции. Нет записи, а в документе героя остался заход прежних версий - он остаётся
     * в памяти помеченным к переносу: первая же запись героя положит его сюда и уберёт из документа героя.
     */
    suspend fun hydrate(hero: Hero): Hero {
        val stored = collection.find(Filters.eq("_id", hero._id)).firstOrNull()?.run
        val legacy = hero.campaign.run
        when {
            stored != null -> {
                hero.campaign.run = stored
                hero.runPrint = print(stored)
            }

            legacy != null -> {
                hero.runPrint = MIGRATE
                hero.loaded = hero.loaded?.minus("campaign")
            }

            else -> hero.runPrint = print(null)
        }
        return hero
    }

    /** Заход героя изменился с чтения: его надо записать вместе с героем. Непрочитанный ([hydrate] не звали) не пишется. */
    fun dirty(hero: Hero): Boolean = hero.runPrint.let { it != null && it != print(hero.campaign.run) }

    /** Пишет заход героя в транзакции [session]: закрытый удаляется. */
    suspend fun write(hero: Hero, session: ClientSession) {
        val run = hero.campaign.run
        if (run == null) {
            collection.deleteOne(session, Filters.eq("_id", hero._id))
        } else {
            collection.replaceOne(session, Filters.eq("_id", hero._id), HeroRun(hero._id, run), ReplaceOptions().upsert(true))
        }
        val print = print(run)
        ru.descend.exileforge.config.afterCommit { hero.runPrint = print }
    }

    suspend fun delete(heroId: String, session: ClientSession) {
        collection.deleteOne(session, Filters.eq("_id", heroId))
    }

    /** Метка захода, который ещё лежит в документе героя: отличается от любого отпечатка, поэтому запись его перенесёт. */
    private const val MIGRATE = "migrate"
}
