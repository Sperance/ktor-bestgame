package config

import base.exception.BaseException
import base.exception.BaseRepositoryExceptions
import com.mongodb.MongoException
import com.mongodb.MongoNotPrimaryException
import com.mongodb.MongoSocketException
import com.mongodb.MongoTimeoutException

/**
 * Ошибки драйвера в отказы приложения (1.53.0). Временные - сеть, таймаут, смена первичного узла, конфликт
 * транзакции - становятся `BRY_007` и ответом 503: клиент ставит команду в очередь и повторяет. Прочие -
 * общей `BRY_001`; текст драйвера (имена коллекций и индексов) остаётся в логе, а не в ответе.
 */
object MongoErrors {
    fun transient(e: Throwable): Boolean = when (e) {
        is MongoSocketException, is MongoTimeoutException, is MongoNotPrimaryException -> true
        is MongoException -> e.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) || e.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
        else -> false
    }

    fun translate(method: String, e: Exception): BaseException = when {
        e is BaseException -> e
        transient(e) -> BaseRepositoryExceptions.funExceptionUnavailable(method, e::class.simpleName)
        else -> BaseRepositoryExceptions.funException(method, e::class.simpleName)
    }
}
