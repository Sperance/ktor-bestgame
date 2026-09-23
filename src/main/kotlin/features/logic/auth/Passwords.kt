package features.logic.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Хеши паролей.
 *
 * До 0.21.0 пароль хранился одним проходом SHA-256 с солью: видеокарта перебирает такое
 * миллиардами вариантов в секунду. Теперь это PBKDF2-SHA256 из самого JDK, с солью и числом
 * итераций внутри строки: `pbkdf2$<итерации>$<соль>$<хеш>`. Так строка описывает себя сама,
 * и поднять число итераций потом можно, не трогая уже записанные хеши.
 *
 * Старый формат ещё проверяется ([verify] получает соль из поля `salt`), и [needsRehash]
 * говорит, когда хеш пора переписать: это делается при следующем удачном входе, и никому
 * не приходится сбрасывать пароль.
 */
object Passwords {

    private const val SCHEME = "pbkdf2"
    const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun hash(password: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return encode(iterations, salt, derive(password, salt, iterations))
    }

    /**
     * Совпадает ли пароль с записанным хешем.
     *
     * @param legacySalt поле `salt` документа - нужно только для хешей до 0.21.0
     */
    fun verify(password: String, stored: String, legacySalt: String = ""): Boolean {
        if (stored.startsWith("$SCHEME$")) {
            val parts = stored.split('$')
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].hexToByteArrayOrNull() ?: return false
            val expected = parts[3].hexToByteArrayOrNull() ?: return false
            return MessageDigest.isEqual(derive(password, salt, iterations), expected)
        }
        if (stored.isEmpty()) return false
        return MessageDigest.isEqual(legacy(password, legacySalt).toByteArray(), stored.toByteArray())
    }

    /** Записан ли хеш старым способом или с меньшим числом итераций, чем сейчас принято. */
    fun needsRehash(stored: String): Boolean =
        !stored.startsWith("$SCHEME$") || stored.split('$').getOrNull(1)?.toIntOrNull()?.let { it < ITERATIONS } != false

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(iterations: Int, salt: ByteArray, hash: ByteArray) =
        "$SCHEME$$iterations$${salt.toHex()}$${hash.toHex()}"

    /** Формат до 0.21.0: SHA-256 от "соль:пароль". */
    private fun legacy(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArrayOrNull(): ByteArray? =
        if (length % 2 != 0) null else runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
