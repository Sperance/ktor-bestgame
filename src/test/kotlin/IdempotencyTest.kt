import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Test
import server.addons.Claim
import server.addons.Idempotency
import server.addons.ReplyStore
import server.addons.StoredReply
import server.addons.installIdempotency
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Повтор команды по `Idempotency-Key`: второй запрос с тем же ключом не выполняет её, а повторяет ответ. */
class IdempotencyTest {

    private class MemoryStore : ReplyStore {
        private val replies = ConcurrentHashMap<String, Pair<String, StoredReply?>>()
        override suspend fun claim(account: String, key: String, request: String): Claim {
            val found = replies.putIfAbsent("$account:$key", request to null) ?: return Claim.Taken
            val reply = found.second
            return when {
                found.first != request -> Claim.Mismatch
                reply != null -> Claim.Done(reply)
                else -> Claim.Busy
            }
        }
        override suspend fun complete(account: String, key: String, reply: StoredReply) {
            replies.computeIfPresent("$account:$key") { _, (request, _) -> request to reply }
        }
        override suspend fun release(account: String, key: String) {
            replies.computeIfPresent("$account:$key") { _, found -> found.takeIf { it.second != null } }
        }
    }

    @Test
    fun a_repeated_key_replays_the_stored_reply_without_running_the_command_again() = testApplication {
        environment { config = MapApplicationConfig() }
        var runs = 0
        application {
            installIdempotency(MemoryStore()) { "account" }
            routing { post("/command") { runs++; call.respondText("run $runs") } }
        }
        val first = client.post("/command") { header(Idempotency.HEADER, "key-00000001") }
        val second = client.post("/command") { header(Idempotency.HEADER, "key-00000001") }
        val other = client.post("/command") { header(Idempotency.HEADER, "key-00000002") }

        assertEquals("run 1", first.bodyAsText())
        assertNull(first.headers[Idempotency.REPLAY_HEADER])
        assertEquals("run 1", second.bodyAsText())
        assertEquals("true", second.headers[Idempotency.REPLAY_HEADER])
        assertEquals("run 2", other.bodyAsText())
        assertEquals(2, runs)
    }
}
