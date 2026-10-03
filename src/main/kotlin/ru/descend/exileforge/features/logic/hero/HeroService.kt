package ru.descend.exileforge.features.logic.hero

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import ru.descend.exileforge.base.exception.model.AuthExceptions
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.exception.model.ProgressionExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.auction.AuctionLotRepository
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.user.User
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.logic.auth.caller
import ru.descend.exileforge.features.logic.guild.GuildService

/**
 * Жизнь героя как сущности аккаунта: создание со стартовым набором и местом на аккаунте, список
 * героев аккаунта, удаление со всеми следами - заходом, лотами, гильдией и слотом аккаунта.
 * Команды живого героя - в сервисах своих систем, его документ - в [HeroRepository].
 */
class HeroService(
    private val heroes: HeroRepository,
    private val users: UserRepository,
    private val lots: AuctionLotRepository,
    private val guilds: GuildService,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index

    private companion object {
        const val MIN_NAME = 2
        const val MAX_NAME = 24
        const val MAX_DESCRIPTION = 200
    }

    /** Герои аккаунта [userId]; неизвестный аккаунт - отказ. */
    suspend fun heroesOf(userId: String): List<Hero> {
        if (users.findByField(User::_id, userId) == null) throw CharacterExceptions.funExceptionUserNotFound("findByUser", userId)
        return heroes.findByUser(userId)
    }

    suspend fun createAll(sources: List<Hero>, session: ClientSession): List<Hero> = sources.map { create(it, session) }

    /**
     * Новый герой. Игрок создаёт героя общим POST и мог прислать в теле что угодно: десятый уровень,
     * мешок золота. От игрока берутся только имя, описание и класс, остальное начинается с нуля и
     * стартового набора ([Starter]); администратор и сидинг пишут как есть. Место на аккаунте
     * занимается в той же транзакции.
     */
    suspend fun create(source: Hero, session: ClientSession): Hero {
        val method = "create"
        val player = caller()?.takeUnless { it.isAdmin }
        if (player != null && source.userId != player.user._id) throw AuthExceptions.funExceptionNotYourAccount(method, source.userId)
        val hero = if (player == null) {
            source
        } else {
            Hero(userId = source.userId, name = source.name.trim(), description = source.description.trim().take(MAX_DESCRIPTION), heroClass = source.heroClass)
        }
        if (hero.name.isBlank()) throw CharacterExceptions.funExceptionName(method)
        // Длина имени (1.53.0): документ и уникальный индекс не раздуваются присланной простынёй
        val longest = minOf(MAX_NAME, index.rules.inputs.heroName)
        if (hero.name.length !in MIN_NAME..longest) throw CharacterExceptions.funExceptionNameLength(method, "$MIN_NAME-$longest")
        val heroClass = index.heroClass(hero.heroClass) ?: throw ProgressionExceptions.funExceptionClassNotFound(method, hero.heroClass)
        // Имя уникально в индексе, поэтому занятым считается и имя мягко удалённого героя
        if (heroes.findByField(Hero::name, hero.name, includeDeleted = true) != null) throw CharacterExceptions.funExceptionNameDuplicate(method, hero.name)
        val owner = users.findByField(User::_id, hero.userId, session) ?: throw CharacterExceptions.funExceptionUserNotFound(method, hero.userId)
        if (owner.countCharacters >= index.rules.maxCharacters) throw CharacterExceptions.funExceptionMaxChars(method, index.rules.maxCharacters.toString())
        Starter.grant(hero, index, heroClass)
        val created = heroes.insert(hero, session)
        owner.countCharacters++
        users.update(owner, session)
        return created
    }

    /** Удаление героя: заход, активные лоты, место в гильдии и слот аккаунта уходят вместе с ним. */
    suspend fun delete(id: String, session: ClientSession) {
        val hero = heroes.findById(id, session) ?: throw CharacterExceptions.funExceptionNotFound("delete", id)
        heroes.deleteById(hero, session)
        lots.deleteActiveBySeller(hero._id, session)
        guilds.forget(hero, session)
        // Место под героя освобождается, иначе после трёх удалений нового не создать
        users.findByField(User::_id, hero.userId, session)?.let { owner ->
            owner.countCharacters = (owner.countCharacters - 1).coerceAtLeast(0)
            users.update(owner, session)
        }
    }
}

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
        rules.orbs.forEach { (code, amount) -> if (index.template(code) != null) hero.earn(code, amount) }
        index.template(index.rules.flasks.starter)?.let { flask ->
            hero.items += factory.create(Hero.newItemId(), flask, flask.rarity, dice).also { it.slot = Slot.FLASK }
        }
        // Оружие (1.2.0) и броня (1.12.0) класса - обычные, надетые сразу: герой не выходит на первую карту с пустыми руками
        (listOf(heroClass.weapon) + heroClass.armour).mapNotNull(index::template).forEach { gear ->
            val weapon = rules.magicWeapon && gear.code == heroClass.weapon
            hero.items += (if (weapon) magicWeapon(factory, gear, dice) else factory.create(Hero.newItemId(), gear, Rarity.COMMON, dice)).also { it.slot = gear.slot }
        }
        index.professions.professions.forEach { profession ->
            index.templatesBySlot[profession.tool]?.firstOrNull { it.code.startsWith(rules.toolPrefix) }?.let { tool ->
                hero.items += factory.create(Hero.newItemId(), tool, Rarity.COMMON, dice).also { it.slot = tool.slot }
            }
        }
    }

    /** Волшебное оружие с одной строкой урона; нет такой в пуле - волшебное как выпало (дно редкости держит фабрика). */
    private fun magicWeapon(factory: ItemFactory, template: ItemTemplate, dice: Dice): ItemInstance {
        val item = factory.create(Hero.newItemId(), template, Rarity.MAGIC, dice)
        val affixes = factory.affixes
        val damage = affixes.affixPool(template).filter { (def) -> def.effects.any { "DAMAGE" in it.stat } }
        val kept = affixes.permanent(item.rolls)
        affixes.rollExtraFrom(damage, template, Rarity.MAGIC, kept, dice, item.itemLevel)?.let { item.rolls = kept + it }
        return item
    }
}
