package base.exception.model

import base.exception.BaseException

object CampaignExceptions {
    open class CampaignException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Campaign", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{CampaignException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionContent(errorMethod: String, value: String? = "") = CampaignException("Campaign content is invalid: $value", errorMethod, "CP_001", listOf(value.orEmpty()))
    fun funExceptionMapNotFound(errorMethod: String, value: String? = "") = CampaignException("Campaign map $value not found", errorMethod, "CP_002", listOf(value.orEmpty()))
    fun funExceptionMapLocked(errorMethod: String, value: String? = "") = CampaignException("Campaign map $value is not open yet", errorMethod, "CP_003", listOf(value.orEmpty()))
    fun funExceptionMonsterNotOnMap(errorMethod: String, value: String? = "") = CampaignException("Monster $value does not live on this map", errorMethod, "CP_004", listOf(value.orEmpty()))
    fun funExceptionNoChest(errorMethod: String, value: String? = "") = CampaignException("No chest left on map $value", errorMethod, "CP_006", listOf(value.orEmpty()))
    fun funExceptionSealed(errorMethod: String, value: String? = "") = CampaignException("The exit of map $value is sealed while its guardian lives", errorMethod, "CP_007", listOf(value.orEmpty()))
    fun funExceptionBossSlain(errorMethod: String, value: String? = "") = CampaignException("The guardian of map $value is already slain", errorMethod, "CP_008", listOf(value.orEmpty()))
    fun funExceptionBossStands(errorMethod: String, value: String? = "") = CampaignException("The guardian of map $value already stands", errorMethod, "CP_010", listOf(value.orEmpty()))
    fun funExceptionMapItem(errorMethod: String, value: String? = "") = CampaignException("Item $value does not open this location", errorMethod, "CP_011", listOf(value.orEmpty()))
    fun funExceptionRarity(errorMethod: String, value: String? = "") = CampaignException("Unknown monster rarity $value", errorMethod, "CP_005", listOf(value.orEmpty()))
    fun funExceptionCorruptionSpent(errorMethod: String, value: String? = "") = CampaignException("The corrupted zone of map $value is already spent this run", errorMethod, "CP_012", listOf(value.orEmpty()))
    fun funExceptionNoCrystal(errorMethod: String, value: String? = "") = CampaignException("No such essence crystal on map $value", errorMethod, "CP_013", listOf(value.orEmpty()))
    fun funExceptionCrystalCorrupted(errorMethod: String, value: String? = "") = CampaignException("This crystal on map $value is already corrupted", errorMethod, "CP_014", listOf(value.orEmpty()))
}
