package ru.descend.exileforge.features.data.auth
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.exileforge.base.entity.StockEntity
import ru.descend.exileforge.extensions.now

/**
 * Одна выданная сессия.
 *
 * Самого токена здесь нет, только его хеш: утёкшая коллекция не даёт войти ни в один аккаунт.
 * Срок скользящий - каждое использование сдвигает [expiresAt], так что активный игрок не
 * вылетает никогда, а брошенный токен умирает сам через [AuthSessionRepository.LIFETIME].
 */
@Serializable
data class AuthSession(
    val userId: String,
    val tokenHash: String,
    var expiresAt: LocalDateTime,
    var lastUsedAt: LocalDateTime = LocalDateTime.now(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    override var _id: String = ObjectId().toHexString(),
) : StockEntity
