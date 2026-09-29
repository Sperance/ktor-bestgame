package features.logic.hero

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Запросы одного героя идут по очереди: два запроса, пришедшие разом, писали бы один документ в
 * двух транзакциях, и одна падала с WriteConflict. Очередь на героя убирает гонку у корня; чужие
 * герои друг друга не ждут.
 */
object HeroLocks {
    private class Entry(val mutex: Mutex = Mutex(), var holders: Int = 0)

    private val locks = ConcurrentHashMap<String, Entry>()

    /** Запись живёт, пока ею кто-то пользуется: иначе карта росла бы от каждого нового `heroId`. */
    suspend fun <T> withLock(heroId: String, block: suspend () -> T): T {
        val entry = locks.compute(heroId) { _, current -> (current ?: Entry()).apply { holders++ } }!!
        try {
            return entry.mutex.withLock { block() }
        } finally {
            locks.computeIfPresent(heroId) { _, current -> current.takeIf { --it.holders > 0 } }
        }
    }
}

fun Application.installHeroLocks() {
    intercept(ApplicationCallPipeline.Call) {
        val heroId = call.request.queryParameters["heroId"]
        if (heroId.isNullOrBlank()) proceed() else HeroLocks.withLock(heroId) { proceed() }
    }
}
