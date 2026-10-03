package base.exception.model

import base.exception.BaseException

object CampaignExceptions {
    open class CampaignException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Campaign", errorMethod, errorCode, messageArgs) {
        override fun toString(): String = "{CampaignException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionContent(errorMethod: String, value: String? = "") = CampaignException("Campaign content is invalid: $value", errorMethod, "CP_001", listOf(value.orEmpty()))
    fun funExceptionMapNotFound(errorMethod: String, value: String? = "") = CampaignException("Campaign map $value not found", errorMethod, "CP_002", listOf(value.orEmpty()))
    fun funExceptionMapLocked(errorMethod: String, value: String? = "") = CampaignException("Campaign map $value is not open yet", errorMethod, "CP_003", listOf(value.orEmpty()))
    fun funExceptionBrew(errorMethod: String, value: String? = "") = CampaignException("$value cannot go into this run", errorMethod, "CP_027", listOf(value.orEmpty()))
    fun funExceptionMapItem(errorMethod: String, value: String? = "") = CampaignException("Item $value does not open this location", errorMethod, "CP_011", listOf(value.orEmpty()))
    fun funExceptionRarity(errorMethod: String, value: String? = "") = CampaignException("Unknown monster rarity $value", errorMethod, "CP_005", listOf(value.orEmpty()))
    fun funExceptionNoRun(errorMethod: String, value: String? = "") = CampaignException("No run is open on map $value", errorMethod, "CP_018", listOf(value.orEmpty()))

    /** Тело больше потолка (1.53.0): 422, а не отказ правила. */
    class PayloadException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : CampaignException(message, errorMethod, errorCode, messageArgs)

    fun funExceptionTooManyEvents(errorMethod: String, value: String? = "") = PayloadException("A journal carries at most $value events", errorMethod, "CP_022", listOf(value.orEmpty()))
    fun funExceptionEventOrder(errorMethod: String, value: String? = "") = CampaignException("Run event $value is out of order", errorMethod, "CP_019", listOf(value.orEmpty()))
    fun funExceptionContentChanged(errorMethod: String, value: String? = "") = CampaignException("The world changed since run $value began: it is closed", errorMethod, "CP_020", listOf(value.orEmpty()))
    fun funExceptionSeedTooSoon(errorMethod: String, value: String? = "") = CampaignException("A new run can begin in $value s", errorMethod, "CP_021", listOf(value.orEmpty()))
    fun funExceptionRegionClosed(errorMethod: String, value: String? = "") = CampaignException("Region $value is not cleared", errorMethod, "CP_024", listOf(value.orEmpty()))

    /** Башня пройдена до потолка (1.68.0): вход отклоняется до списания печати. */
    fun funExceptionTowerConquered(errorMethod: String, value: String? = "") = CampaignException("The tower is conquered up to floor $value", errorMethod, "CP_025", listOf(value.orEmpty()))

    /** Журнал чужого захода (1.68.0): его `runId` не совпал с открытым заходом - ни одно событие не принято. */
    fun funExceptionRunMismatch(errorMethod: String, value: String? = "") = CampaignException("The journal belongs to run $value, which is not the open one", errorMethod, "CP_026", listOf(value.orEmpty()))
    fun funExceptionNoTrial(errorMethod: String, value: String? = "") = CampaignException("No trial is open", errorMethod, "CP_023", listOf(value.orEmpty()))
}
