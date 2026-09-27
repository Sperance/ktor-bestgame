package features.data.hero

import base.exception.model.AuthExceptions
import base.exception.model.CharacterExceptions
import base.exception.model.ProgressionExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import config.ContentStore
import config.MongoFactory.transactionExecute
import features.data.auction.AuctionLotRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.auth.caller
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class HeroRepository : BaseRepository<Hero>(Hero::class), KoinComponent {
    private val users: UserRepository by inject()
    private val lots: AuctionLotRepository by inject()
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
     * принёс вещь - выдача администратора, старый документ, правка контента, - пустой она не ляжет.
     */
    override suspend fun settle(entity: Hero) {
        val factory = ItemFactory(index)
        val dice by lazy { Dice.system() }
        (entity.items.asSequence() + entity.overflow.asSequence()).forEach { item -> index.template(item.template)?.let { factory.reconcile(it, item, dice) } }
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

    suspend fun requireHero(heroId: String, method: String): Hero =
        requireById(heroId) { CharacterExceptions.funExceptionNotFound(method, it) }

    /** Одна запись героя своей транзакцией: версия проверяется, объект в памяти идёт в ногу с базой. */
    suspend fun save(hero: Hero, method: String): Hero {
        transactionExecute(method) { session -> update(hero, session) }
        return hero
    }
}

/**
 * Стартовый набор нового героя: узел класса, первые умения, сферы, фляга на поясе, инструмент каждой
 * профессии в своём слоте и по вещи на редкость правила. Заполняет только пустое - администратор
 * может прислать героя готовым.
 */
object Starter {
    fun grant(hero: Hero, index: ContentIndex, heroClass: HeroClass) {
        val dice = Dice.system()
        val factory = ItemFactory(index)
        val rules = index.rules.starter
        if (hero.tree.isEmpty()) hero.tree += TakenNode(heroClass.startNode)
        if (hero.skills.learned.isEmpty()) hero.skills = index.skillRules.starter(heroClass.code)
        if (hero.bag.isEmpty()) Orb.entries.forEach { orb -> hero.bag[orb.name] = rules.orbs }
        if (hero.items.isNotEmpty()) return
        index.template(index.rules.flasks.starter)?.let { flask ->
            hero.items += factory.create(Hero.newItemId(), flask, flask.rarity, dice).also { it.slot = Slot.FLASK }
        }
        index.professions.professions.forEach { profession ->
            index.templatesBySlot[profession.tool]?.firstOrNull { it.code.startsWith(rules.toolPrefix) }?.let { tool ->
                hero.items += factory.create(Hero.newItemId(), tool, Rarity.COMMON, dice).also { it.slot = tool.slot }
            }
        }
        rules.gear.forEach { rarity ->
            dice.pickOrNull(index.content.equipment.templates.filter { it.rarity == rarity && !it.slot.isTool && !it.slot.isFlask && !it.slot.isJewelLike })
                ?.let { hero.items += factory.create(Hero.newItemId(), it, rarity, dice) }
        }
    }
}
