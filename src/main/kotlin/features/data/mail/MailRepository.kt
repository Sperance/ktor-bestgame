package features.data.mail

import base.exception.BaseException
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import config.ContentStore
import config.MongoFactory.transactionExecute
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.data.user.UserRepository
import features.logic.hero.Stash
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Почта аккаунтов (1.69.0): системные письма о статусах отчётов и письма администратора одному или всем. Письмо живёт
 * [MAIL_DAYS] дней; истёкшие уходят при чтении ящика. Вложение забирает один герой аккаунта, один раз, одной транзакцией
 * с героем.
 */
class MailRepository :
    BaseRepository<Mail>(Mail::class),
    KoinComponent {
    private val heroes: HeroRepository by inject()
    private val users: UserRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index

    override val indexes = listOf(IndexSpec.on("userId", "expiresAt"))

    /** Ящик аккаунта: живые письма, новые первыми; истёкшие удаляются тут же. */
    suspend fun inbox(userId: String): List<Mail> {
        val now = System.currentTimeMillis()
        collection.deleteMany(Filters.and(Filters.eq("userId", userId), Filters.lte("expiresAt", now)))
        return findByFilter(Filters.and(Filters.eq("userId", userId), Filters.gt("expiresAt", now))).sortedByDescending { it.createdAt }
    }

    suspend fun markRead(userId: String, id: String): Mail = mine(userId, id, "markRead").also { mail ->
        if (!mail.read) {
            mail.read = true
            transactionExecute("mail read $id") { session -> update(mail, session) }
        }
    }

    suspend fun remove(userId: String, id: String) {
        val mail = mine(userId, id, "remove")
        transactionExecute("mail delete $id") { session -> collection.deleteOne(session, Filters.eq("_id", mail._id)) }
    }

    /** Вложение письма [id] - герою [heroId] этого аккаунта: золото, стопки, вещи на его уровне; второй раз - отказ `ML_002`. */
    suspend fun claim(userId: String, id: String, heroId: String): Mail {
        val method = "claim"
        val mail = mine(userId, id, method)
        if (mail.claimedBy != null || mail.attachment.empty) throw mailError("Mail $id has nothing to claim", method, "ML_002")
        val hero = heroes.requireHero(heroId, method)
        if (hero.userId != userId) throw mailError("Hero $heroId is not of this account", method, "ML_001")
        give(hero, mail.attachment)
        mail.claimedBy = heroId
        mail.read = true
        return transactionExecute("mail claim $id") { session ->
            heroes.update(hero, session)
            update(mail, session)
            mail
        }
    }

    private fun give(hero: Hero, attachment: MailAttachment) {
        hero.money += attachment.gold.coerceAtLeast(0)
        attachment.items.filterKeys { index.item(it) != null }.forEach { (code, amount) -> if (amount > 0) hero.earn(code, amount) }
        attachment.instances.forEach { Stash.giveBack(hero, it, index) }
        val factory = ItemFactory(index)
        attachment.equipment.forEach { piece ->
            val template = index.template(piece.template) ?: return@forEach
            Stash.receive(
                hero,
                factory.create(
                    Hero.newItemId(),
                    template,
                    piece.rarity ?: template.rarity,
                    Dice.system(),
                    level = index.rules.loot.itemLevel(hero.level),
                ),
                index,
            )
        }
    }

    /** Системное письмо аккаунту [userId]: ключ словаря и его подстановки. */
    suspend fun system(userId: String, key: String, args: List<String>) {
        transactionExecute("mail system") { session -> insert(Mail(userId, MailKind.SYSTEM, key, args, expiresAt = expiry()), session) }
    }

    /** Системное письмо с вложением в транзакции вызывающего (1.74.0): возврат товара аукциона. */
    suspend fun system(userId: String, key: String, args: List<String>, attachment: MailAttachment, session: ClientSession) {
        insert(Mail(userId, MailKind.SYSTEM, key, args, attachment = attachment, expiresAt = expiry()), session)
    }

    /** Письмо администратора: одному аккаунту по логину или каждому аккаунту; сколько писем ушло. */
    suspend fun send(request: MailRequest): Int {
        val method = "send"
        val subject = request.subject.trim().take(index.rules.inputs.mailSubject)
        val body = request.body.trim().take(index.rules.inputs.mailBody)
        if (subject.isEmpty()) throw mailError("Mail subject is empty", method, "ML_003")
        request.attachment.items.keys.forEach { if (index.item(it) == null) throw mailError("Unknown item $it", method, "ML_004", listOf(it)) }
        request.attachment.equipment.forEach { if (index.template(it.template) == null) throw mailError("Unknown template ${it.template}", method, "ML_004", listOf(it.template)) }
        val targets = if (request.login.isBlank()) {
            users.findByFilter(Filters.eq("isActive", true)).map { it._id }
        } else {
            listOf(users.findByLogin(request.login.trim())?._id ?: throw mailError("No account ${request.login}", method, "ML_005", listOf(request.login)))
        }
        val expiresAt = expiry()
        transactionExecute("mail send ${targets.size}") { session ->
            targets.forEach { insert(Mail(it, MailKind.ADMIN, subject = subject, body = body, attachment = request.attachment, expiresAt = expiresAt), session) }
        }
        return targets.size
    }

    private suspend fun mine(userId: String, id: String, method: String): Mail = findById(id)?.takeIf { it.userId == userId && it.expiresAt > System.currentTimeMillis() } ?: throw mailError("Mail $id not found", method, "ML_006", listOf(id))

    private fun expiry(): Long = System.currentTimeMillis() + MAIL_DAYS * DAY_MS

    private companion object {
        const val MAIL_DAYS = 30
        const val DAY_MS = 24 * 3_600_000L
    }
}

private fun mailError(message: String, method: String, code: String, args: List<String> = emptyList()) = BaseException(message, "Mail", method, code, args)
