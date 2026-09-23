package features.data.auth

import base.entity.StockEntity
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

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
