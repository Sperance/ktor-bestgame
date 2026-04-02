package base.exception

/**
 * Иерархия бизнес-исключений.
 * StatusPages перехватывает каждый тип и возвращает нужный HTTP-код.
 */
sealed class AppException(
    override val message: String,
    val httpCode: Int
) : RuntimeException(message)

class NotFoundException(message: String) : AppException(message, 404)

class BadRequestException(message: String) : AppException(message, 400)

class ConflictException(message: String) : AppException(message, 409)

class OptimisticLockException(
    entityName: String,
    id: Long,
    expectedVersion: Long
) : AppException(
    message = "$entityName(id=$id) was modified by another transaction. " +
            "Expected version=$expectedVersion. Please re-fetch and retry.",
    httpCode = 409
)