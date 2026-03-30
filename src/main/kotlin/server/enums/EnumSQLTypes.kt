package server.enums

enum class EnumSQLTypes(val textValue: String) {
    VARCHAR_10("VARCHAR(10)"),
    VARCHAR_100("VARCHAR(100)"),
    VARCHAR_255("VARCHAR(255)"),
    VARCHAR_500("VARCHAR(500)"),
    TEXT("TEXT"),
    BOOL("BOOL"),
    INTEGER("INTEGER"),
    DECIMAL("DECIMAL"),
    SMALLINT("SMALLINT"),
    TIMESTAMP("timestamp without time zone");

    companion object {
        fun getFromName(value: String, defaultValue: EnumSQLTypes? = null): EnumSQLTypes? {
            return entries.find { it.name.equals(value.trim(), ignoreCase = true) }?:defaultValue
        }
    }
}