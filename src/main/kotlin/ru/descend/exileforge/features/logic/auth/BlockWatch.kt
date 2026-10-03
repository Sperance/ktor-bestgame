package ru.descend.exileforge.features.logic.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.descend.exileforge.extensions.printLog
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import kotlin.time.Duration.Companion.minutes

/**
 * Дозор блок-листа (1.46.0): блок-лист правится руками в Mongo, поэтому раз в минуту кэш перечитывается, а у
 * аккаунтов под блокировкой гасятся все сессии - заблокированный выходит сразу, не дожидаясь конца сессии.
 */
class BlockWatch(private val cache: BlockListCache, private val sessions: AuthSessionRepository) {
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            delay(1.minutes)
            runCatching {
                cache.initializeCache()
                cache.blockedUsers().forEach { sessions.revokeAll(it) }
            }.onFailure { printLog("[BlockWatch] ${it.message}", true) }
        }
    }
}
