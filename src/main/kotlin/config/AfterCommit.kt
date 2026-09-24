package config

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Действия, отложенные до коммита транзакции (с 0.49.0).
 *
 * [MongoFactory.transactionExecute] кладёт элемент в контекст корутины на время тела
 * транзакции и запускает накопленное после коммита. Так кеши справочников не уезжают
 * вперёд базы: откат транзакции - и правка в кеш не попадает.
 */
class AfterCommit : AbstractCoroutineContextElement(AfterCommit) {
    companion object Key : CoroutineContext.Key<AfterCommit>

    private val actions = ArrayList<() -> Unit>()

    fun add(action: () -> Unit) = synchronized(actions) { actions += action }

    fun run() = synchronized(actions) { actions.forEach { it() }; actions.clear() }
}

/** Выполнить после коммита текущей транзакции; вне транзакции - сразу. */
suspend fun afterCommit(action: () -> Unit) {
    coroutineContext[AfterCommit]?.add(action) ?: action()
}
