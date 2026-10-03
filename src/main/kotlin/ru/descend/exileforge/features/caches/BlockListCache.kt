package ru.descend.exileforge.features.caches
import kotlinx.datetime.LocalDateTime
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.features.data.blockList.BlockList
import ru.descend.exileforge.features.data.blockList.BlockListRepository

/**
 * Блок-лист (1.46.0): запись блокирует адрес, аккаунт (`user_id`) или оба, пока не истёк её срок `expiredAt`.
 * Правится прямо в Mongo; кэш перечитывается раз в минуту ([ru.descend.exileforge.features.logic.auth.BlockWatch]).
 */
class BlockListCache(repository: BlockListRepository) : MongoCache<BlockList, BlockListRepository>(repository) {
    init {
        repository.cache = this
    }

    private fun active(): List<BlockList> = LocalDateTime.now().let { now -> getCache().filter { !it.deleted && it.expiredAt > now } }

    fun isBlocked(address: String): Boolean = active().any { it.address.isNotBlank() && it.address == address }

    fun isUserBlocked(userId: String): Boolean = active().any { it.user_id == userId }

    /** Аккаунты под действующей блокировкой - их сессии гасятся. */
    fun blockedUsers(): Set<String> = active().mapNotNullTo(HashSet()) { it.user_id?.takeIf(String::isNotBlank) }
}
