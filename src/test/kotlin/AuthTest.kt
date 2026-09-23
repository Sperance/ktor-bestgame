import features.logic.auth.AccessPolicy
import features.logic.auth.AccessPolicy.Need
import features.logic.auth.Passwords
import features.logic.auth.Tokens
import java.security.MessageDigest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Вход и доступ - то, что проверяется без базы.
 *
 * Таблица доступа проверена целиком здесь, потому что ошибка в ней - это не упавший тест,
 * а открытая дверь: маршрут, который должен требовать администратора, молча пускает всех.
 */
class AuthTest {

    // ==================== Пароли ====================

    @Test
    fun a_password_verifies_against_its_own_hash_and_nothing_else() {
        val hash = Passwords.hash("Secret1", iterations = 1_000)
        assertTrue(Passwords.verify("Secret1", hash))
        assertFalse(Passwords.verify("secret1", hash))
        assertFalse(Passwords.verify("", hash))
        // Соль своя у каждого хеша: один пароль не даёт двух одинаковых строк.
        assertNotEquals(hash, Passwords.hash("Secret1", iterations = 1_000))
    }

    @Test
    fun a_hash_from_before_0_21_still_signs_in_and_asks_to_be_rewritten() {
        val salt = "abc123"
        val legacy = MessageDigest.getInstance("SHA-256").digest("$salt:Secret1".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(Passwords.verify("Secret1", legacy, salt))
        assertFalse(Passwords.verify("Secret2", legacy, salt))
        assertTrue(Passwords.needsRehash(legacy))
    }

    @Test
    fun a_weaker_hash_is_rewritten_and_a_current_one_is_not() {
        assertTrue(Passwords.needsRehash(Passwords.hash("Secret1", iterations = 1_000)))
        assertFalse(Passwords.needsRehash("pbkdf2\$${Passwords.ITERATIONS}\$00\$00"))
    }

    @Test
    fun a_broken_or_empty_hash_never_verifies() {
        assertFalse(Passwords.verify("anything", ""))
        assertFalse(Passwords.verify("anything", "pbkdf2\$notanumber\$00\$00"))
        assertFalse(Passwords.verify("anything", "pbkdf2\$1000\$zz\$00"))
    }

    // ==================== Токены ====================

    @Test
    fun tokens_are_unique_and_only_their_hash_is_kept() {
        val a = Tokens.issue()
        val b = Tokens.issue()
        assertNotEquals(a, b)
        assertEquals(64, Tokens.hash(a).length)
        assertNotEquals(a, Tokens.hash(a))
        assertEquals(Tokens.hash(a), Tokens.hash(a))
    }

    @Test
    fun only_a_bearer_header_carries_a_token() {
        val token = Tokens.issue()
        assertEquals(token, Tokens.fromHeader("Bearer $token"))
        assertEquals(token, Tokens.fromHeader("bearer $token"))
        assertNull(Tokens.fromHeader(null))
        assertNull(Tokens.fromHeader("Basic $token"))
        assertNull(Tokens.fromHeader("Bearer"))
        assertNull(Tokens.fromHeader("Bearer short"))
    }

    // ==================== Доступ ====================

    private fun need(method: String, path: String, vararg query: Pair<String, String>) =
        AccessPolicy.need(method, path) { name -> query.firstOrNull { it.first == name }?.second }

    @Test
    fun only_signing_in_and_static_files_are_open() {
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/login"))
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/login/byDeviceId"))
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/byDeviceId"))
        assertEquals(Need.PUBLIC, need("GET", "/locale/index.json"))
        assertEquals(Need.PUBLIC, need("GET", "/locale/zh.json"))
        assertEquals(Need.PUBLIC, need("GET", "/icons/icons.json"))
        assertEquals(Need.PUBLIC, need("GET", "/system/routes"))
        assertEquals(Need.PUBLIC, need("GET", "/system/version"))
        assertEquals(Need.PUBLIC, need("GET", "/system/health"))
        // The old GET login is gone, and a GET to the login path is not a way in.
        assertEquals(Need.SIGNED_IN, need("GET", "/api/v1/user/login"))
    }

    @Test
    fun everything_a_player_does_needs_a_session() {
        listOf(
            "GET" to "/api/v1/character/inventory/stats",
            "POST" to "/api/v1/characterequipment/applyOrb",
            "POST" to "/api/v1/characterequipment/sell",
            "POST" to "/api/v1/auctionlot/buy",
            "GET" to "/api/v1/auctionlot/search",
            "POST" to "/api/v1/character/skilltree/reset",
            "POST" to "/api/v1/redemptioncodes/useRedeptionCode",
            "GET" to "/api/v1/user/me",
            "POST" to "/api/v1/user/logout",
            "POST" to "/api/v1/user/changePassword",
            "GET" to "/api/v1/equipment",
            "GET" to "/api/v1/modifierdefinition",
            "GET" to "/api/v1/skilltreenode/paged",
        ).forEach { (method, path) -> assertEquals(Need.SIGNED_IN, need(method, path), "$method $path") }
    }

    @Test
    fun writing_through_the_generic_crud_is_for_an_administrator() {
        listOf("equipment", "items", "modifierdefinition", "user", "redemptioncodes", "auctionlot", "characterequipment")
            .forEach { collection ->
                listOf("POST", "PUT", "DELETE").forEach { method ->
                    assertEquals(Need.ADMIN, need(method, "/api/v1/$collection"), "$method $collection")
                }
            }
        // A role, a balance or a level is never a player's to write.
        assertEquals(Need.ADMIN, need("PUT", "/api/v1/character", "id" to "x"))
    }

    @Test
    fun a_player_creates_and_releases_their_own_character_themselves() {
        assertEquals(Need.SIGNED_IN, need("POST", "/api/v1/character"))
        assertEquals(Need.SIGNED_IN, need("DELETE", "/api/v1/character", "id" to "x"))
        assertEquals(Need.SIGNED_IN, need("GET", "/api/v1/character", "id" to "x"))
        // Without an id it is the whole server's list of characters.
        assertEquals(Need.ADMIN, need("GET", "/api/v1/character"))
        assertEquals(Need.ADMIN, need("GET", "/api/v1/character/paged"))
    }

    @Test
    fun what_belongs_to_other_players_is_read_by_an_administrator_only() {
        AccessPolicy.privateCollections.forEach { collection ->
            if (collection == "character") return@forEach
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection"), collection)
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection/paged"), collection)
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection/count"), collection)
        }
        assertEquals(Need.ADMIN, need("GET", "/api/v1/user/search/active"))
    }

    @Test
    fun granting_and_the_system_switches_are_for_an_administrator() {
        assertEquals(Need.ADMIN, need("POST", "/api/v1/character/inventory/itemToInventory"))
        assertEquals(Need.ADMIN, need("POST", "/api/v1/character/inventory/experience"))
        assertEquals(Need.ADMIN, need("POST", "/api/v1/character/inventory/addItem"))
        assertEquals(Need.ADMIN, need("POST", "/system/shutdown"))
        assertEquals(Need.ADMIN, need("GET", "/system/exceptions"))
    }

    @Test
    fun a_trailing_slash_is_not_a_way_around_the_table() {
        assertEquals(Need.ADMIN, need("POST", "/api/v1/equipment/"))
        assertEquals(Need.ADMIN, need("GET", "/api/v1/user/"))
    }
}
