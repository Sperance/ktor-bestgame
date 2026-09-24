package config

import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Хуки транзакции (с 0.49.0).
 *
 * [MongoFactory.transactionExecute] кладёт элемент в контекст корутины на время тела
 * транзакции. [beforeCommit] - последняя запись перед коммитом, по одному разу на ключ:
 * так десять вставок в инвентарь двигают ревизию героя одним `$inc`. [afterCommit] - то,
 * что нельзя делать, пока запись может откатиться: правка кешей, журнал изменений.
 */
class TransactionHooks : AbstractCoroutineContextElement(TransactionHooks) {
    companion object Key : CoroutineContext.Key<TransactionHooks>

    private val before = LinkedHashMap<Any, suspend (ClientSession) -> Unit>()
    private val after = ArrayList<() -> Unit>()

    fun beforeCommit(key: Any, action: suspend (ClientSession) -> Unit) = synchronized(before) { before.putIfAbsent(key, action); Unit }

    fun afterCommit(action: () -> Unit) = synchronized(after) { after += action; Unit }

    suspend fun runBeforeCommit(session: ClientSession) {
        val actions = synchronized(before) { before.values.toList().also { before.clear() } }
        actions.forEach { it(session) }
    }

    fun runAfterCommit() = synchronized(after) { after.forEach { it() }; after.clear() }
}

/** Выполнить после коммита текущей транзакции; вне транзакции - сразу. */
suspend fun afterCommit(action: () -> Unit) {
    coroutineContext[TransactionHooks]?.afterCommit(action) ?: action()
}

/** Выполнить один раз на [key] перед коммитом текущей транзакции; вне транзакции - сразу на [session]. */
suspend fun beforeCommit(key: Any, session: ClientSession, action: suspend (ClientSession) -> Unit) {
    coroutineContext[TransactionHooks]?.beforeCommit(key, action) ?: action(session)
}
