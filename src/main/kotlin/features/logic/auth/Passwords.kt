package features.logic.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Хеши паролей: PBKDF2-SHA256 из самого JDK, с солью и числом итераций внутри строки:
 * `pbkdf2$<итерации>$<соль>$<хеш>`. Так строка описывает себя сама, и поднять число итераций
 * потом можно, не трогая записанные хеши: [needsRehash] говорит, когда хеш пора переписать.
 */
object Passwords {

    private const val SCHEME = "pbkdf2"
    /** 210 000 (1.53.0, OWASP 2023): 600 000 стоили 0,6 с ядра на вход, и десяток входов в секунду клал сервер. */
    const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    /** Подставной хеш (1.46.0): с ним сверяется пароль неизвестного логина, чтобы ответ шёл столько же, сколько с известным. */
    val DECOY: String by lazy { hash("decoy-${random.nextLong()}") }

    fun hash(password: String, iterations: Int = ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return encode(iterations, salt, derive(password, salt, iterations))
    }

    /** Совпадает ли пароль с записанным хешем. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$').takeIf { it.size == 4 && it[0] == SCHEME } ?: return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = parts[2].hexToByteArrayOrNull() ?: return false
        val expected = parts[3].hexToByteArrayOrNull() ?: return false
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    /** Записан ли хеш чужим способом или с другим числом итераций, чем сейчас принято (1.53.0: и с большим - он дороже на каждом входе). */
    fun needsRehash(stored: String): Boolean =
        !stored.startsWith("$SCHEME$") || stored.split('$').getOrNull(1)?.toIntOrNull()?.let { it != ITERATIONS } != false

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

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArrayOrNull(): ByteArray? =
        if (length % 2 != 0) null else runCatching { chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
}
