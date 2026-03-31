package application.enums

import application.model.IntStat

enum class EnumStatBool(val code: String): IntStat {
    IS_ALIVE("BL0"),
    IS_BANNED("BL1")
}