package config

import com.mongodb.MongoException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Body retries must be explicitly enabled by callers whose state is rebuilt inside the transaction.
 * An ambiguous commit retries only commit, never the body. */
internal suspend fun <T> runTransaction(
    start: () -> Unit,
    active: () -> Boolean,
    commit: suspend () -> Unit,
    abort: suspend () -> Unit,
    maxAttempts: Int = 1,
    maxCommitAttempts: Int = 5,
    pause: suspend (Int) -> Unit = { attempt -> delay((50L shl (attempt - 1).coerceAtMost(4)) + kotlin.random.Random.nextLong(50)) },
    body: suspend () -> T
): T {
    require(maxAttempts > 0 && maxCommitAttempts > 0)
    var attempt = 0
    while (true) {
        attempt++
        var ambiguousCommit = false
        try {
            start()
            val result = body()
            if (active()) {
                var commitAttempt = 0
                while (true) {
                    commitAttempt++
                    try {
                        commit()
                        ambiguousCommit = false
                        break
                    } catch (e: MongoException) {
                        ambiguousCommit = e.hasErrorLabel("UnknownTransactionCommitResult")
                        if (!ambiguousCommit || commitAttempt >= maxCommitAttempts) throw e
                        pause(commitAttempt)
                    }
                }
            }
            return result
        } catch (e: Throwable) {
            // Do not misrepresent an unknown commit result as a rolled-back transaction.
            if (!ambiguousCommit && active()) {
                withContext(NonCancellable) {
                    try { abort() } catch (abortError: Throwable) { if (abortError !== e) e.addSuppressed(abortError) }
                }
            }
            if (e is CancellationException || ambiguousCommit || e !is MongoException ||
                !e.hasErrorLabel("TransientTransactionError") || attempt >= maxAttempts) throw e
            pause(attempt)
        }
    }
}
