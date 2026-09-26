package base.exception.model

import base.exception.BaseException

/** Отказы книги умений и слотов (с 0.69.0). */
object SkillExceptions {
    open class SkillException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Skill", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{SkillException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionContent(errorMethod: String, value: String? = "") = SkillException("Skill content is invalid: $value", errorMethod, "SK_001", listOf(value.orEmpty()))
    fun funExceptionNotFound(errorMethod: String, value: String? = "") = SkillException("Skill $value not found", errorMethod, "SK_002", listOf(value.orEmpty()))
    fun funExceptionOtherClass(errorMethod: String, value: String? = "") = SkillException("Skill $value belongs to another class", errorMethod, "SK_003", listOf(value.orEmpty()))
    fun funExceptionNoBook(errorMethod: String, value: String? = "") = SkillException("There is no book of $value in the bag", errorMethod, "SK_004", listOf(value.orEmpty()))
    fun funExceptionMaxLevel(errorMethod: String, value: String? = "") = SkillException("Skill $value is already at its highest level", errorMethod, "SK_005", listOf(value.orEmpty()))
    fun funExceptionRequirement(errorMethod: String, skill: String = "", level: String = "", need: String = "") =
        SkillException("Skill $skill level $level needs $need", errorMethod, "SK_006", listOf(skill, level, need))
    fun funExceptionNotLearned(errorMethod: String, value: String? = "") = SkillException("Skill $value is not learned", errorMethod, "SK_007", listOf(value.orEmpty()))
    fun funExceptionSlotLocked(errorMethod: String, value: String? = "") = SkillException("This slot opens at level $value", errorMethod, "SK_008", listOf(value.orEmpty()))
    fun funExceptionWrongKind(errorMethod: String, value: String? = "") = SkillException("Skill $value does not fit this slot", errorMethod, "SK_009", listOf(value.orEmpty()))
    fun funExceptionCondition(errorMethod: String, value: String? = "") = SkillException("Condition $value does not fit this slot", errorMethod, "SK_010", listOf(value.orEmpty()))
    fun funExceptionExchange(errorMethod: String, value: String? = "") = SkillException("The exchange needs $value different skill books", errorMethod, "SK_011", listOf(value.orEmpty()))
    fun funExceptionSlot(errorMethod: String, value: String? = "") = SkillException("There is no slot $value", errorMethod, "SK_012", listOf(value.orEmpty()))
}
