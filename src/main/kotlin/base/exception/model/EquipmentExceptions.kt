package base.exception.model

import application.enums.EnumEquipmentType
import base.exception.BaseException

object EquipmentExceptions {
    open class EquipmentException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Equipment", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{EquipmentException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = EquipmentException(value, errorMethod, "EQ_001", listOf(value.orEmpty()))
    fun funExceptionType(errorMethod: String, value: String? = "") = EquipmentException("Equipment type is not supported", errorMethod, "EQ_002", listOf(value.orEmpty()))

    // Валидация типов предметов
    fun funExceptionInvalidSlot(errorMethod: String, equipmentType: String) =
        EquipmentException("Invalid slot for equipment type $equipmentType", errorMethod, "EQ_003", listOf(equipmentType))

    fun funExceptionMissingData(errorMethod: String, missingField: String) =
        EquipmentException("Missing required data: $missingField", errorMethod, "EQ_004", listOf(missingField))
}