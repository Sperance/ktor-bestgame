package features.data.hero

import CONST_FIELD_UPDATED
import CONST_FIELD_VERSION
import base.exception.model.AuthExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.ProgressionExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import config.ContentStore
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.auction.AuctionLotRepository
import features.data.guild.GuildRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.auth.caller
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import org.bson.Document
import org.bson.conversions.Bson
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HeroRepository : BaseRepository<Hero>(Hero::class), KoinComponent {
    private val users: UserRepository by inject()
    private val lots: AuctionLotRepository by inject()
    private val guilds: GuildRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    override val indexes = listOf(IndexSpec.unique("idx_unique_name", "name"), IndexSpec.on("userId"))

    /**
     * Игрок создаёт героя общим POST и мог прислать в теле что угодно: десятый уровень, мешок золота.
     * От игрока берутся только имя, описание и класс, остальное начинается с нуля; администратор и
     * сидинг пишут как есть.
     */
    override suspend fun admit(entity: Hero): Hero =
        if (caller()?.isAdmin != false) entity
        else Hero(userId = entity.userId, name = entity.name.trim(), description = entity.description, heroClass = entity.heroClass)

    /**
     * Перед каждой записью героя его копии сверяются с контентом (1.1.0): пропавшие описания уходят,
     * закреплённые строки дороллены, волшебная и редкая доведены до дна редкости. Какой бы путь ни
     * принёс вещь - выдача администратора, старый документ, правка контента, витрина торговца, - пустой
     * она не ляжет.
     */
    override suspend fun settle(entity: Hero) {
        val factory = ItemFactory(index)
        val dice by lazy { Dice.system() }
        (entity.items.asSequence() + entity.overflow.asSequence() + entity.merchant?.offers.orEmpty().asSequence().map { it.item })
            .forEach { item -> index.template(item.template)?.let { factory.reconcile(it, item, dice) } }
    }

    override suspend fun validateBeforeInsert(entity: Hero, session: ClientSession) {
        val method = "validateBeforeInsert"
        val player = caller()?.takeUnless { it.isAdmin }
        if (player != null && entity.userId != player.user._id) throw AuthExceptions.funExceptionNotYourAccount(method, entity.userId)
        if (entity.name.isBlank()) throw CharacterExceptions.funExceptionName(method)
        val heroClass = index.heroClass(entity.heroClass) ?: throw ProgressionExceptions.funExceptionClassNotFound(method, entity.heroClass)
        // Имя уникально в индексе, поэтому занятым считается и имя мягко удалённого героя
        if (findByField(Hero::name, entity.name, includeDeleted = true) != null) throw CharacterExceptions.funExceptionNameDuplicate(method, entity.name)
        val owner = users.findByField(User::_id, entity.userId, session) ?: throw CharacterExceptions.funExceptionUserNotFound(method, entity.userId)
        if (owner.countCharacters >= index.rules.maxCharacters) throw CharacterExceptions.funExceptionMaxChars(method, index.rules.maxCharacters.toString())
        Starter.grant(entity, index, heroClass)
    }

    override suspend fun validateAfterInsert(entity: Hero, session: ClientSession) {
        val owner = users.findByField(User::_id, entity.userId, session) ?: throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        owner.countCharacters++
        if (owner.countCharacters > index.rules.maxCharacters) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert", index.rules.maxCharacters.toString())
        users.update(owner, session)
    }

    override suspend fun validateAfterDelete(entity: Hero, session: ClientSession) {
        lots.deleteActiveBySeller(entity._id, session)
        guilds.forget(entity, session)
        // Место под героя освобождается, иначе после трёх удалений нового не создать
        users.findByField(User::_id, entity.userId, session)?.let { owner ->
            owner.countCharacters = (owner.countCharacters - 1).coerceAtLeast(0)
            users.update(owner, session)
        }
    }

    /** Герои одного игрока - то, из чего он выбирает при входе; их не больше трёх, страниц нет. */
    suspend fun findByUser(userId: String): List<Hero> {
        if (users.findByField(User::_id, userId) == null) throw CharacterExceptions.funExceptionUserNotFound("findByUser", userId)
        return findByFilter(Filters.eq("userId", userId))
    }

    /** Владелец героя - одно поле по `_id`: доступ спрашивает его на каждой команде. */
    suspend fun ownerOf(heroId: String): String? =
        collection.withDocumentClass<Document>().find(readFilter(Filters.eq("_id", heroId)))
            .projection(Projections.include("userId")).limit(1).firstOrNull()?.getString("userId")

    /** Имя, класс и уровень героев [ids] - три поля без тайника: для состава и заявок гильдии. */
    suspend fun cards(ids: Collection<String>): Map<String, HeroCard> {
        if (ids.isEmpty()) return emptyMap()
        return collection.withDocumentClass<Document>().find(readFilter(Filters.`in`("_id", ids)))
            .projection(Projections.include("name", "heroClass", "level")).toList()
            .associate { doc -> doc.getString("_id") to HeroCard(doc.getString("_id"), doc.getString("name").orEmpty(), doc.getString("heroClass").orEmpty(), doc.getInteger("level", 1)) }
    }

    /**
     * Правка гильдии у нескольких героев разом, мимо объектов в памяти: версия каждого растёт, так что
     * копия, прочитанная до правки, запишется только гонкой и перечитается.
     */
    suspend fun patchGuild(filter: Bson, update: Bson, session: ClientSession) {
        collection.updateMany(session, readFilter(filter), Updates.combine(update, Updates.inc(CONST_FIELD_VERSION, 1L), Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now())))
    }

    suspend fun requireHero(heroId: String, method: String): Hero =
        requireById(heroId) { CharacterExceptions.funExceptionNotFound(method, it) }

    /** Одна запись героя своей транзакцией: версия проверяется, объект в памяти идёт в ногу с базой. */
    suspend fun save(hero: Hero, method: String): Hero {
        transactionExecute(method) { session -> update(hero, session) }
        return hero
    }
}

/** Герой в составе гильдии: только то, что видно в списке. */
data class HeroCard(val id: String, val name: String, val heroClass: String, val level: Int)

/**
 * Стартовый набор нового героя (1.12.0): узел класса, первые умения, золото на первые покупки, оружие и броня
 * класса надетыми, фляга на поясе и инструмент каждой профессии в своём слоте. Заполняет только пустое -
 * администратор может прислать героя готовым.
 */
object Starter {
    fun grant(hero: Hero, index: ContentIndex, heroClass: HeroClass) {
        val dice = Dice.system()
        val factory = ItemFactory(index)
        val rules = index.rules.starter
        if (hero.tree.isEmpty()) hero.tree += TakenNode(heroClass.startNode)
        if (hero.skills.learned.isEmpty()) hero.skills = index.skillRules.starter(heroClass.code)
        if (hero.items.isNotEmpty()) return
        hero.money += rules.gold
        index.template(index.rules.flasks.starter)?.let { flask ->
            hero.items += factory.create(Hero.newItemId(), flask, flask.rarity, dice).also { it.slot = Slot.FLASK }
        }
        // Оружие (1.2.0) и броня (1.12.0) класса - обычные, надетые сразу: герой не выходит на первую карту с пустыми руками
        (listOf(heroClass.weapon) + heroClass.armour).mapNotNull(index::template).forEach { gear ->
            hero.items += factory.create(Hero.newItemId(), gear, Rarity.COMMON, dice).also { it.slot = gear.slot }
        }
        index.professions.professions.forEach { profession ->
            index.templatesBySlot[profession.tool]?.firstOrNull { it.code.startsWith(rules.toolPrefix) }?.let { tool ->
                hero.items += factory.create(Hero.newItemId(), tool, Rarity.COMMON, dice).also { it.slot = tool.slot }
            }
        }
    }
}
