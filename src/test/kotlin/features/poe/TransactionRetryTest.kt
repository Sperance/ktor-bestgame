package features.poe

import com.mongodb.MongoException
import config.runTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.*

class TransactionRetryTest {
    private fun failure(label: String) = MongoException(112, label).apply { addLabel(label) }
    private class Session {
        var starts = 0
        var commits = 0
        var aborts = 0
        var active = false
        fun start() { starts++; active = true }
        fun abort() { aborts++; active = false }
    }
    @Test fun retriesWholeTransactionAfterTransientCommitFailure() = runBlocking {
        val session = Session(); var bodies = 0
        val result = runTransaction(session::start, { session.active }, {
            session.commits++
            if (session.commits == 1) throw failure("TransientTransactionError")
            session.active = false
        }, session::abort, maxAttempts = 3, pause = {}) { ++bodies }
        assertEquals(2, result); assertEquals(2, bodies); assertEquals(2, session.starts); assertEquals(1, session.aborts)
    }
    @Test fun unknownCommitRetriesCommitWithoutReplayingBody() = runBlocking {
        val session = Session(); var bodies = 0
        runTransaction(session::start, { session.active }, {
            session.commits++
            if (session.commits == 1) throw failure("UnknownTransactionCommitResult")
            session.active = false
        }, session::abort, maxAttempts = 3, pause = {}) { bodies++ }
        assertEquals(1, bodies); assertEquals(2, session.commits); assertEquals(0, session.aborts)
    }
    @Test fun exhaustedUnknownCommitIsNotAbortedOrReplayed() = runBlocking {
        val session = Session(); var bodies = 0
        val error = failure("UnknownTransactionCommitResult")
        assertSame(error, assertFailsWith<MongoException> {
            runTransaction(session::start, { session.active }, { session.commits++; throw error }, session::abort,
                maxAttempts = 3, maxCommitAttempts = 2, pause = {}) { bodies++ }
        })
        assertEquals(1, bodies); assertEquals(2, session.commits); assertEquals(0, session.aborts)
    }
    @Test fun transientRetryHasFiniteBudget() = runBlocking {
        val session = Session(); var bodies = 0
        assertFailsWith<MongoException> {
            runTransaction(session::start, { session.active }, {}, session::abort, maxAttempts = 3, pause = {}) {
                bodies++; throw failure("TransientTransactionError")
            }
        }
        assertEquals(3, bodies); assertEquals(3, session.aborts)
    }
    @Test fun cancellationAndBusinessErrorsAreNotRetriedAndAbortDoesNotMaskCause() = runBlocking {
        for (error in listOf(CancellationException("cancel"), IllegalArgumentException("validation"))) {
            val session = Session()
            val thrown = assertFailsWith<Exception> {
                runTransaction(session::start, { session.active }, {}, { session.abort(); error("abort failed") }, maxAttempts = 3, pause = {}) { throw error }
            }
            assertSame(error, thrown); assertEquals(1, session.starts); assertEquals(1, session.aborts)
            assertEquals("abort failed", thrown.suppressed.single().message)
        }
    }
    @Test fun legacyBodiesDoNotOptIntoReplay() = runBlocking {
        val session = Session()
        assertFailsWith<MongoException> {
            runTransaction(session::start, { session.active }, {}, session::abort, pause = {}) { throw failure("TransientTransactionError") }
        }
        assertEquals(1, session.starts)
    }
}
