package base.exception

object BaseRepositoryExceptions {

    open class BaseRepositoryException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "BaseRepository", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{BaseRepositoryException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = BaseRepositoryException(value, errorMethod, "BRY_001", listOf(value.orEmpty()))
    fun funExceptionRace(errorMethod: String, value: String? = "") = BaseRepositoryException("Error in race condition: $value. Refetch and try again", errorMethod, "BRY_002", listOf(value.orEmpty()))
    fun funExceptionInsertInvalid(errorMethod: String, value: String? = "") = BaseRepositoryException("Invalid insertion data for table", errorMethod, "BRY_003", listOf(value.orEmpty()))
    fun funExceptionInsertVersion(errorMethod: String, value: String? = "") = BaseRepositoryException("New entity field 'version' must be 0. Currene version: $value", errorMethod, "BRY_004", listOf(value.orEmpty()))
    fun funExceptionFindId(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity with id '$value' not found", errorMethod, "BRY_005", listOf(value.orEmpty()))
    fun funExceptionEntityNull(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity is null", errorMethod, "BRY_006", listOf(value.orEmpty()))
    fun funExceptionEntityClass(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity class $value does not support", errorMethod, "BRY_007", listOf(value.orEmpty()))
    fun funExceptionVersioned(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity class $value is not versioned", errorMethod, "BRY_008", listOf(value.orEmpty()))
}