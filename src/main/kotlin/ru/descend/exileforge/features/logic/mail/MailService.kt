package ru.descend.exileforge.features.logic.mail

import com.mongodb.client.model.Filters
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.mail.Mail
import ru.descend.exileforge.features.data.mail.MailAttachment
import ru.descend.exileforge.features.data.mail.MailKind
import ru.descend.exileforge.features.data.mail.MailRepository
import ru.descend.exileforge.features.data.mail.MailRequest
import ru.descend.exileforge.features.data.mail.mailError
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.logic.hero.Stash

/** Почта как игра (1.69.0): вложение уходит герою, письмо администратора - аккаунтам. Ящик - в [MailRepository]. */
class MailService(
    private val mails: MailRepository,
    private val heroes: HeroRepository,
    private val users: UserRepository,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index

    /** Вложение письма [id] - герою [heroId] этого аккаунта: золото, стопки, вещи на его уровне; второй раз - отказ `ML_002`. */
    suspend fun claim(userId: String, id: String, heroId: String): Mail {
        val method = "claim"
        val mail = mails.mine(userId, id, method)
        if (mail.claimedBy != null || mail.attachment.empty) throw mailError("Mail $id has nothing to claim", method, "ML_002")
        val hero = heroes.requireHero(heroId, method)
        if (hero.userId != userId) throw mailError("Hero $heroId is not of this account", method, "ML_001")
        give(hero, mail.attachment)
        mail.claimedBy = heroId
        mail.read = true
        return transactionExecute("mail claim $id") { session ->
            heroes.update(hero, session)
            mails.update(mail, session)
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
        val expiresAt = mails.expiry()
        transactionExecute("mail send ${targets.size}") { session ->
            targets.forEach { mails.insert(Mail(it, MailKind.ADMIN, subject = subject, body = body, attachment = request.attachment, expiresAt = expiresAt), session) }
        }
        return targets.size
    }
}
