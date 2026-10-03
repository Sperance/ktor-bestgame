package base.exception.model

import base.exception.BaseException

object GuildExceptions {
    open class GuildException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Guild", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{GuildException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionNotFound(errorMethod: String, value: String? = "") = GuildException("Guild $value not found", errorMethod, "GU_001", listOf(value.orEmpty()))
    fun funExceptionAlreadyMember(errorMethod: String, value: String? = "") = GuildException("Character already belongs to guild $value", errorMethod, "GU_002", listOf(value.orEmpty()))
    fun funExceptionNotMember(errorMethod: String, value: String? = "") = GuildException("Character $value is not in a guild", errorMethod, "GU_003", listOf(value.orEmpty()))
    fun funExceptionCreateLevel(errorMethod: String, value: String? = "") = GuildException("Founding a guild needs character level $value", errorMethod, "GU_004", listOf(value.orEmpty()))
    fun funExceptionName(errorMethod: String, value: String? = "") = GuildException("Guild name must be $value characters long", errorMethod, "GU_005", listOf(value.orEmpty()))
    fun funExceptionNameTaken(errorMethod: String, value: String? = "") = GuildException("Guild name $value is taken", errorMethod, "GU_006", listOf(value.orEmpty()))
    fun funExceptionTag(errorMethod: String, value: String? = "") = GuildException("Guild tag must be $value letters or digits", errorMethod, "GU_007", listOf(value.orEmpty()))
    fun funExceptionTagTaken(errorMethod: String, value: String? = "") = GuildException("Guild tag $value is taken", errorMethod, "GU_008", listOf(value.orEmpty()))
    fun funExceptionFaction(errorMethod: String, value: String? = "") = GuildException("Unknown guild faction $value", errorMethod, "GU_009", listOf(value.orEmpty()))
    fun funExceptionEmblem(errorMethod: String, value: String? = "") = GuildException("Unknown guild emblem or color $value", errorMethod, "GU_010", listOf(value.orEmpty()))
    fun funExceptionRights(errorMethod: String, value: String? = "") = GuildException("Guild role $value may not do this", errorMethod, "GU_011", listOf(value.orEmpty()))
    fun funExceptionFull(errorMethod: String, value: String? = "") = GuildException("Guild is full: $value members", errorMethod, "GU_012", listOf(value.orEmpty()))
    fun funExceptionRejoin(errorMethod: String, value: String? = "") = GuildException("Character may join a guild again in $value", errorMethod, "GU_013", listOf(value.orEmpty()))
    fun funExceptionMinLevel(errorMethod: String, value: String? = "") = GuildException("Guild accepts characters from level $value", errorMethod, "GU_014", listOf(value.orEmpty()))
    fun funExceptionMode(errorMethod: String, value: String? = "") = GuildException("Guild admission is $value", errorMethod, "GU_015", listOf(value.orEmpty()))
    fun funExceptionApplication(errorMethod: String, value: String? = "") = GuildException("Guild application of $value not found", errorMethod, "GU_016", listOf(value.orEmpty()))
    fun funExceptionInvite(errorMethod: String, value: String? = "") = GuildException("Guild invitation $value not found", errorMethod, "GU_017", listOf(value.orEmpty()))
    fun funExceptionMember(errorMethod: String, value: String? = "") = GuildException("Guild member $value not found", errorMethod, "GU_018", listOf(value.orEmpty()))
    fun funExceptionOfficers(errorMethod: String, value: String? = "") = GuildException("Guild has no more than $value officers", errorMethod, "GU_019", listOf(value.orEmpty()))
    fun funExceptionLeaderLeaves(errorMethod: String, value: String? = "") = GuildException("Guild leader must hand over leadership before leaving $value", errorMethod, "GU_020", listOf(value.orEmpty()))
    fun funExceptionAmount(errorMethod: String, value: String? = "") = GuildException("Contribution amount $value must be positive", errorMethod, "GU_021", listOf(value.orEmpty()))
    fun funExceptionDailyLimit(errorMethod: String, value: String? = "") = GuildException("Daily contribution limit reached, $value left today", errorMethod, "GU_022", listOf(value.orEmpty()))
    fun funExceptionNotOrb(errorMethod: String, value: String? = "") = GuildException("Only gold and currency orbs can be contributed, $value is neither", errorMethod, "GU_023", listOf(value.orEmpty()))
    fun funExceptionAnnouncement(errorMethod: String, value: String? = "") = GuildException("Guild announcement is at most $value characters", errorMethod, "GU_027", listOf(value.orEmpty()))
    fun funExceptionApplied(errorMethod: String, value: String? = "") = GuildException("Application to guild $value is already sent", errorMethod, "GU_028", listOf(value.orEmpty()))
    fun funExceptionInvited(errorMethod: String, value: String? = "") = GuildException("Character $value is already invited", errorMethod, "GU_029", listOf(value.orEmpty()))
    fun funExceptionHeroName(errorMethod: String, value: String? = "") = GuildException("No character named $value", errorMethod, "GU_030", listOf(value.orEmpty()))
    fun funExceptionMinLevelValue(errorMethod: String, value: String? = "") = GuildException("Guild minimum level $value is out of range", errorMethod, "GU_031", listOf(value.orEmpty()))
    fun funExceptionNode(errorMethod: String, value: String? = "") = GuildException("Guild tree node $value cannot be taken", errorMethod, "GU_033", listOf(value.orEmpty()))
    fun funExceptionRespec(errorMethod: String, value: String? = "") = GuildException("The guild tree can be reset again at $value", errorMethod, "GU_034", listOf(value.orEmpty()))
    fun funExceptionStashFull(errorMethod: String, value: String? = "") = GuildException("Guild stash tab $value is full", errorMethod, "GU_035", listOf(value.orEmpty()))
    fun funExceptionTakes(errorMethod: String, value: String? = "") = GuildException("All $value takes from the guild stash are used today", errorMethod, "GU_036", listOf(value.orEmpty()))
    fun funExceptionTabRank(errorMethod: String, value: String? = "") = GuildException("Guild stash tab $value needs a higher rank", errorMethod, "GU_037", listOf(value.orEmpty()))
    fun funExceptionStashEntry(errorMethod: String, value: String? = "") = GuildException("Guild stash has no $value", errorMethod, "GU_038", listOf(value.orEmpty()))
    fun funExceptionTarget(errorMethod: String, value: String? = "") = GuildException("This cannot be done to guild member $value", errorMethod, "GU_032", listOf(value.orEmpty()))
}
