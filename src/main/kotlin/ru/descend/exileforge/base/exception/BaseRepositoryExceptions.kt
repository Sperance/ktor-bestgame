package ru.descend.exileforge.base.exception
object BaseRepositoryExceptions {

    open class BaseRepositoryException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "BaseRepository", errorMethod, errorCode, messageArgs) {
        /** Ошибка драйвера (таймаут, сеть, WriteConflict) и гонка версий проходят на повторе. */
        override val isTransient: Boolean get() = errorCode in TRANSIENT_CODES

        override fun toString(): String = "{BaseRepositoryException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    private val TRANSIENT_CODES = setOf("BRY_001", "BRY_002", "BRY_007")

    /** База временно недоступна (1.53.0): сеть, таймаут, смена первичного узла, конфликт транзакции - ответ 503, клиент повторяет. */
    const val UNAVAILABLE = "BRY_007"

    fun funException(errorMethod: String, value: String? = "") = BaseRepositoryException(value, errorMethod, "BRY_001", listOf(value.orEmpty()))
    fun funExceptionRace(errorMethod: String, value: String? = "") = BaseRepositoryException("Error in race condition: $value. Refetch and try again", errorMethod, "BRY_002", listOf(value.orEmpty()))
    fun funExceptionInsertInvalid(errorMethod: String, value: String? = "") = BaseRepositoryException("Invalid insertion data for table", errorMethod, "BRY_003", listOf(value.orEmpty()))
    fun funExceptionInsertVersion(errorMethod: String, value: String? = "") = BaseRepositoryException("New entity field 'version' must be 0. Currene version: $value", errorMethod, "BRY_004", listOf(value.orEmpty()))
    fun funExceptionFindId(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity with id '$value' not found", errorMethod, "BRY_005", listOf(value.orEmpty()))
    fun funExceptionUnavailable(errorMethod: String, value: String? = "") = BaseRepositoryException("Database is busy or unavailable, try again", errorMethod, UNAVAILABLE, listOf(value.orEmpty()))
    fun funExceptionEntityNull(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity is null", errorMethod, "BRY_006", listOf(value.orEmpty()))
}
