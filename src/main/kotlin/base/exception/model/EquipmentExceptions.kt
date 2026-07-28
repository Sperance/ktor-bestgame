package base.exception.model

import application.enums.EnumEquipmentType
import base.exception.BaseException

object EquipmentExceptions {
    open class EquipmentException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Equipment", errorMethod, errorCode) {
        override fun toString(): String {
            return "{EquipmentException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = EquipmentException(value, errorMethod, "EQ_001")
    fun funExceptionType(errorMethod: String, value: String? = "") = EquipmentException("Equipment type is not supported", errorMethod, "EQ_002")

    // Валидация типов предметов
    fun funExceptionInvalidSlot(errorMethod: String, equipmentType: String) =
        EquipmentException("Invalid slot for equipment type $equipmentType", errorMethod, "EQ_003")

    fun funExceptionMissingData(errorMethod: String, missingField: String) =
        EquipmentException("Missing required data: $missingField", errorMethod, "EQ_004")
}