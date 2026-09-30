package base.repository

import base.exception.BaseException
import base.exception.BaseRepositoryExceptions
import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoWriteException
import config.MongoErrors
import extensions.printLog

/** Код MongoDB «дубликат уникального ключа». */
internal const val DUPLICATE_KEY = 11000

/**
 * Ошибки драйвера при записи - в ошибки репозитория (1.63.0, вынесено из [BaseRepository]): дубликат уникального ключа -
 * гонка, всё прочее - общая ошибка с именем операции. Бизнес-ошибки проходят как есть. Текст драйвера - в лог, клиенту код.
 */
internal inline fun <R> writing(collection: String, method: String, block: () -> R): R = try {
    block()
} catch (e: BaseException) {
    throw e
} catch (e: MongoWriteException) {
    printLog("[$collection] $method: ${e.message}", true)
    throw if (e.code == DUPLICATE_KEY) BaseRepositoryExceptions.funExceptionRace(method, "duplicate") else BaseRepositoryExceptions.funException(method, "write")
} catch (e: MongoBulkWriteException) {
    printLog("[$collection] $method: ${e.writeErrors.firstOrNull()?.message ?: e.message}", true)
    throw BaseRepositoryExceptions.funException(method, "bulk")
} catch (e: Exception) {
    printLog("[$collection] $method: ${e::class.simpleName}: ${e.message}", true)
    throw MongoErrors.translate(method, e)
}
