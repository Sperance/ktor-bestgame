package ru.descend.infrastructure.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import ru.descend.shared.http.invalid

/** Versioned PBKDF2 encoding. Legacy SHA-256 hashes are accepted only for migration at login. */
object PasswordHasher {
    private const val ITERATIONS = 600_000
    fun validate(password: String) { if (password.length !in 12..128) invalid("Password must contain 12 to 128 characters") }
    fun hash(password: String): String {
        validate(password)
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return listOf("pbkdf2-sha256", ITERATIONS.toString(), Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS))).joinToString("$")
    }
    fun verify(password: String, encoded: String, legacySalt: String): Boolean {
        if (password.length > 128) return false
        if (!encoded.startsWith("pbkdf2-sha256$")) {
            val expected = MessageDigest.getInstance("SHA-256").digest("$legacySalt:$password".toByteArray()).joinToString("") { "%02x".format(it) }
            return MessageDigest.isEqual(expected.toByteArray(), encoded.toByteArray())
        }
        return runCatching {
            val parts = encoded.split('$'); require(parts.size == 4)
            val rounds = parts[1].toInt(); require(rounds in 100_000..2_000_000)
            MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[2]), rounds), Base64.getDecoder().decode(parts[3]))
        }.getOrDefault(false)
    }
    /** Login migration must also support a valid legacy password shorter than the new policy. */
    fun upgrade(password: String): String {
        val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return listOf("pbkdf2-sha256", ITERATIONS.toString(), Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS))).joinToString("$")
    }
    private fun derive(password: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
