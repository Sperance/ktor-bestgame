package base.exception.model

import base.exception.BaseException

object SkillTreeExceptions {
    open class SkillTreeException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "SkillTree", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{SkillTreeException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = SkillTreeException(value, errorMethod, "ST_001", listOf(value.orEmpty()))
    fun funExceptionNodeNotFound(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value not found", errorMethod, "ST_004", listOf(value.orEmpty()))
    fun funExceptionNotTaken(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not taken", errorMethod, "ST_006", listOf(value.orEmpty()))
    fun funExceptionNoStart(errorMethod: String, value: String? = "") = SkillTreeException("Character must take a START node first", errorMethod, "ST_010", listOf(value.orEmpty()))
    fun funExceptionSocketBusy(errorMethod: String, value: String? = "") = SkillTreeException("Jewel socket $value is not empty", errorMethod, "ST_014", listOf(value.orEmpty()))
    fun funExceptionNoRegret(errorMethod: String, value: String? = "") = SkillTreeException("Character has no Orb of Regret left (need $value)", errorMethod, "ST_015", listOf(value.orEmpty()))
    fun funExceptionNotSocket(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not a jewel socket", errorMethod, "ST_016", listOf(value.orEmpty()))
    fun funExceptionNotJewel(errorMethod: String, value: String? = "") = SkillTreeException("Item $value is not a jewel", errorMethod, "ST_017", listOf(value.orEmpty()))
    fun funExceptionNotAdjacent(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not reachable from the taken ones", errorMethod, "ST_007", listOf(value.orEmpty()))
    fun funExceptionChoice(errorMethod: String, value: String? = "") = SkillTreeException("Skill node needs one of its options, or takes none ($value)", errorMethod, "ST_018", listOf(value.orEmpty()))
    fun funExceptionNotRechoosable(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value is not an attribute node whose choice can be changed", errorMethod, "ST_019", listOf(value.orEmpty()))
    fun funExceptionNoChaos(errorMethod: String, value: String? = "") = SkillTreeException("Character has no Chaos Orb to change the choice ($value)", errorMethod, "ST_020", listOf(value.orEmpty()))
    fun funExceptionClassOnly(errorMethod: String, value: String? = "") = SkillTreeException("Skill node $value belongs to another class", errorMethod, "ST_021", listOf(value.orEmpty()))
}
