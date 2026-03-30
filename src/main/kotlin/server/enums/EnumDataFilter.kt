package server.enums

enum class EnumDataFilter {
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    CONTAINS,
    NOT_CONTAINS;

    companion object {
        fun getFromName(value: String, defaultValue: EnumDataFilter? = null): EnumDataFilter? {
            return entries.find { it.name.equals(value.trim(), ignoreCase = true) }?:defaultValue
        }
    }
}