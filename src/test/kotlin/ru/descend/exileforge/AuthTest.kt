package ru.descend.exileforge

import org.junit.Test
import ru.descend.exileforge.features.logic.auth.AccessPolicy
import ru.descend.exileforge.features.logic.auth.AccessPolicy.Need
import ru.descend.exileforge.features.logic.auth.Passwords
import ru.descend.exileforge.features.logic.auth.Tokens
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

    private fun need(method: String, path: String, vararg query: Pair<String, String>) = AccessPolicy.need(method, path) { name -> query.firstOrNull { it.first == name }?.second }

    @Test
    fun only_signing_in_and_static_files_are_open() {
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/login"))
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/login/byDeviceId"))
        assertEquals(Need.PUBLIC, need("POST", "/api/v1/user/byDeviceId"))
        assertEquals(Need.PUBLIC, need("GET", "/locale/index.json"))
        assertEquals(Need.PUBLIC, need("GET", "/locale/ru.json"))
        assertEquals(Need.PUBLIC, need("GET", "/icons/icons.json"))
        assertEquals(Need.PUBLIC, need("GET", "/portraits/index.json"))
        assertEquals(Need.PUBLIC, need("GET", "/portraits/class/WITCH.svg"))
        assertEquals(Need.PUBLIC, need("GET", "/content/modifiers.json"))
        assertEquals(Need.PUBLIC, need("GET", "/system/health"))
        assertEquals(Need.ADMIN, need("GET", "/system/routes"))
        assertEquals(Need.ADMIN, need("GET", "/swagger"))
        assertEquals(Need.PUBLIC, need("GET", "/static/index.json"))
        // The old GET login is gone, and a GET to the login path is not a way in.
        assertEquals(Need.SIGNED_IN, need("GET", "/api/v1/user/login"))
    }

    @Test
    fun everything_a_player_does_needs_a_session() {
        listOf(
            "GET" to "/api/v1/hero/view",
            "POST" to "/api/v1/hero/orb",
            "POST" to "/api/v1/hero/sell",
            "GET" to "/api/v1/hero/bench",
            "POST" to "/api/v1/hero/craft",
            "POST" to "/api/v1/hero/campaign/start",
            "POST" to "/api/v1/hero/campaign/events",
            "POST" to "/api/v1/auctionlot/buy",
            "GET" to "/api/v1/auctionlot/search",
            "POST" to "/api/v1/hero/skilltree/reset",
            "POST" to "/api/v1/redemptioncodes/redeem",
            "GET" to "/api/v1/user/me",
            "POST" to "/api/v1/user/logout",
            "POST" to "/api/v1/user/changePassword",
        ).forEach { (method, path) -> assertEquals(Need.SIGNED_IN, need(method, path), "$method $path") }
    }

    @Test
    fun writing_through_the_generic_crud_is_for_an_administrator() {
        listOf("user", "redemptioncodes", "auctionlot", "blocklist")
            .forEach { collection ->
                listOf("POST", "PUT", "DELETE").forEach { method ->
                    assertEquals(Need.ADMIN, need(method, "/api/v1/$collection"), "$method $collection")
                }
            }
        // A role, a balance or a level is never a player's to write.
        assertEquals(Need.ADMIN, need("PUT", "/api/v1/hero", "id" to "x"))
    }

    @Test
    fun a_player_creates_and_releases_their_own_hero_themselves() {
        assertEquals(Need.SIGNED_IN, need("POST", "/api/v1/hero"))
        assertEquals(Need.SIGNED_IN, need("DELETE", "/api/v1/hero", "id" to "x"))
        assertEquals(Need.SIGNED_IN, need("GET", "/api/v1/hero", "id" to "x"))
        // Without an id it is the whole server's list of heroes.
        assertEquals(Need.ADMIN, need("GET", "/api/v1/hero"))
        assertEquals(Need.ADMIN, need("GET", "/api/v1/hero/paged"))
    }

    @Test
    fun what_belongs_to_other_players_is_read_by_an_administrator_only() {
        AccessPolicy.privateCollections.forEach { collection ->
            if (collection == "hero") return@forEach
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection"), collection)
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection/paged"), collection)
            assertEquals(Need.ADMIN, need("GET", "/api/v1/$collection/count"), collection)
        }
    }

    @Test
    fun granting_is_for_a_tester_and_the_system_switches_for_an_administrator() {
        assertEquals(Need.TESTER, need("POST", "/api/v1/hero/grant/equipment"))
        assertEquals(Need.TESTER, need("POST", "/api/v1/hero/grant/experience"))
        assertEquals(Need.TESTER, need("POST", "/api/v1/hero/grant/item"))
        assertEquals(Need.ADMIN, need("POST", "/api/v1/admin/testers"))
        assertEquals(Need.ADMIN, need("GET", "/system/unknown"))
    }

    @Test
    fun a_trailing_slash_is_not_a_way_around_the_table() {
        assertEquals(Need.ADMIN, need("POST", "/api/v1/redemptioncodes/"))
        assertEquals(Need.ADMIN, need("GET", "/api/v1/user/"))
    }

    @Test
    fun empty_segments_and_escapes_are_not_a_way_around_the_table() {
        assertEquals(Need.ADMIN, need("POST", "//api/v1/redemptioncodes"))
        assertEquals(Need.ADMIN, need("PUT", "/api/v1//hero", "id" to "x"))
        assertEquals(Need.ADMIN, need("GET", "/api/v1/%68ero"))
        assertEquals(Need.TESTER, need("POST", "/api/v1/hero/grant/%49tem"))
        assertEquals("/api/v1/hero", AccessPolicy.canonical("//api//v1/%68ero/"))
    }
}
