package base.repository

import base.exception.BaseException

object BaseRepositoryExceptions {

    open class BaseRepositoryException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "BaseRepository", errorMethod, errorCode) {
        override fun toString(): String {
            return "{BaseRepositoryException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = BaseRepositoryException(value, errorMethod, "BR_001")
    fun funExceptionRace(errorMethod: String, value: String? = "") = BaseRepositoryException("Error in race condition: $value. Refetch and try again", errorMethod, "BR_002")
    fun funExceptionInsertInvalid(errorMethod: String, value: String? = "") = BaseRepositoryException("Invalid insertion data for table", errorMethod, "BR_003")
    fun funExceptionInsertVersion(errorMethod: String, value: String? = "") = BaseRepositoryException("New entity field 'version' must be 0. Currene version: $value", errorMethod, "BR_004")
    fun funExceptionFindId(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity with id '$value' not found", errorMethod, "BR_005")
    fun funExceptionEntityNull(errorMethod: String, value: String? = "") = BaseRepositoryException("Entity is null", errorMethod, "BR_006")
}