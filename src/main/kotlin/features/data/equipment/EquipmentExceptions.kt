package features.data.equipment

import base.exception.BaseException

object EquipmentExceptions {
    open class EquipmentException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Equipment", errorMethod, errorCode) {
        override fun toString(): String {
            return "{EquipmentException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = EquipmentException(value, errorMethod, "EQ_001")
    fun funExceptionType(errorMethod: String, value: String? = "") = EquipmentException("Equipment type is not supported", errorMethod, "EQ_002")
}