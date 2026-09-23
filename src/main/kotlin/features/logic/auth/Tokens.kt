package features.logic.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Токены сессий.
 *
 * Токен - 32 случайных байта, у него 256 бит энтропии, поэтому хранится быстрый SHA-256 от
 * него, а не медленный хеш, как у пароля: подбирать нечего. В базе лежит только хеш - утёкшая
 * коллекция сессий не даёт войти ни в один аккаунт.
 */
object Tokens {

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun issue(): String = encoder.encodeToString(ByteArray(32).also(random::nextBytes))

    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII)).joinToString("") { "%02x".format(it) }

    /** Значение заголовка `Authorization: Bearer <токен>`, или null, если его нет или он чужой формы. */
    fun fromHeader(header: String?): String? {
        if (header == null) return null
        val (scheme, value) = header.trim().split(' ', limit = 2).takeIf { it.size == 2 } ?: return null
        if (!scheme.equals("Bearer", ignoreCase = true)) return null
        return value.trim().takeIf { it.length in 20..200 }
    }
}
