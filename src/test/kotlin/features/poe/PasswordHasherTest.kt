package features.poe

import kotlin.test.*
import java.security.MessageDigest
import ru.descend.infrastructure.security.PasswordHasher

class PasswordHasherTest {
    @Test fun saltedHashesVerifyWithoutStoringThePassword() {
        val password = "Long-password-42"
        val first = PasswordHasher.hash(password); val second = PasswordHasher.hash(password)
        assertNotEquals(first, second); assertFalse(first.contains(password))
        assertTrue(PasswordHasher.verify(password, first, "")); assertFalse(PasswordHasher.verify("incorrect", first, ""))
        assertFalse(PasswordHasher.verify(password, "pbkdf2-sha256$" + "invalid", ""))
    }
    @Test fun legacyHashesCanBeMigratedEvenForOldShortPasswords() {
        val password = "Old123"; val salt = "legacy"
        val legacy = MessageDigest.getInstance("SHA-256").digest("$salt:$password".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(PasswordHasher.verify(password, legacy, salt))
        assertTrue(PasswordHasher.verify(password, PasswordHasher.upgrade(password), ""))
    }
}
