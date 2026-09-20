package base.exception.model

import base.exception.BaseException

object SkillTreeExceptions {
    open class SkillTreeException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "SkillTree", errorMethod, errorCode) {
        override fun toString(): String {
            return "{SkillTreeException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = SkillTreeException(value, errorMethod, "ST_001")
    fun funExceptionCode(errorMethod: String, value: String? = "") = SkillTreeException("Skill node code '$value' is null or empty", errorMethod, "ST_002")
    fun funExceptionCost(errorMethod: String, value: String? = "") = SkillTreeException("Skill node cost $value must not be negative", errorMethod, "ST_003")
    fun funExceptionNodeNotFound(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value not found", errorMethod, "ST_004")
    fun funExceptionAlreadyTaken(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is already taken", errorMethod, "ST_005")
    fun funExceptionNotTaken(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not taken", errorMethod, "ST_006")
    fun funExceptionNotConnected(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not connected to any taken node", errorMethod, "ST_007")
    fun funExceptionNoPoints(errorMethod: String, value: String? = "") = SkillTreeException("Character has not enough skill points ($value)", errorMethod, "ST_008")
    fun funExceptionStartTaken(errorMethod: String, value: String? = "") = SkillTreeException("Character already started the tree from $value", errorMethod, "ST_009")
    fun funExceptionWrongStart(errorMethod: String, value: String? = "") = SkillTreeException("Character must start the tree from its own class node ($value)", errorMethod, "ST_013")
    fun funExceptionNoStart(errorMethod: String, value: String? = "") = SkillTreeException("Character must take a START node first", errorMethod, "ST_010")
    fun funExceptionWouldDetach(errorMethod: String, value: String? = "") = SkillTreeException("Refunding $value would detach other taken nodes from the start", errorMethod, "ST_011")
    fun funExceptionStartRefund(errorMethod: String, value: String? = "") = SkillTreeException("START node $value can only be refunded by a full reset", errorMethod, "ST_012")
}
