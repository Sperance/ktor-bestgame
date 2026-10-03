package ru.descend.exileforge.features.data.mail

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import ru.descend.exileforge.base.exception.BaseException
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.config.MongoFactory.transactionExecute

/**
 * Почта аккаунтов (1.69.0): системные письма о статусах отчётов и письма администратора одному или всем. Письмо живёт
 * `settings.mailDays` дней; истёкшие уходят при чтении ящика. Вложение забирает один герой аккаунта, один раз, одной транзакцией
 * с героем.
 */
class MailRepository(private val settings: ru.descend.exileforge.config.ServerSettings) : BaseRepository<Mail>(Mail::class) {
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

    /** Системное письмо аккаунту [userId]: ключ словаря и его подстановки. */
    suspend fun system(userId: String, key: String, args: List<String>) {
        transactionExecute("mail system") { session -> insert(Mail(userId, MailKind.SYSTEM, key, args, expiresAt = expiry()), session) }
    }

    /** Системное письмо с вложением в транзакции вызывающего (1.74.0): возврат товара аукциона. */
    suspend fun system(userId: String, key: String, args: List<String>, attachment: MailAttachment, session: ClientSession) {
        insert(Mail(userId, MailKind.SYSTEM, key, args, attachment = attachment, expiresAt = expiry()), session)
    }

    /** Живое письмо [id] аккаунта [userId]; чужое или истёкшее - `ML_001`. */
    suspend fun mine(userId: String, id: String, method: String): Mail = findById(id)?.takeIf { it.userId == userId && it.expiresAt > System.currentTimeMillis() } ?: throw mailError("Mail $id not found", method, "ML_006", listOf(id))

    fun expiry(): Long = System.currentTimeMillis() + settings.mailDays * ru.descend.exileforge.extensions.Millis.DAY
}

fun mailError(message: String, method: String, code: String, args: List<String> = emptyList()) = BaseException(message, "Mail", method, code, args)
