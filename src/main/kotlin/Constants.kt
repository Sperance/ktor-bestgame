const val CONST_FIELD_ID = "_id"
const val CONST_FIELD_VERSION  = "version"
const val CONST_FIELD_DELETED  = "deleted"
const val CONST_FIELD_UPDATED  = "updatedAt"
const val CONST_FIELD_CREATED  = "createdAt"
val CONST_SYSTEM_FIELDS = listOf("_id", "id", "version", "deleted", "createdAt", "updatedAt")

const val CONST_USER_MAX_CHARACTERS = 3
const val CONST_API_VERSION = 1

/**
 * Разделитель простого предмета в инвентаре персонажа.
 *
 * Простые предметы хранятся плоским массивом строк вида "chaos_orb:50",
 * где слева id предмета, справа его количество.
 */
const val CONST_ITEM_SEPARATOR = ":"

/**
 * Максимальное количество одного простого предмета в инвентаре.
 */
const val CONST_ITEM_MAX_AMOUNT = 100_000_000_000L

/**
 * Сколько сфер каждого вида выдаётся персонажу при первом сиде.
 */
const val CONST_SEED_ORBS_AMOUNT = 20L

const val MONGO_URI = "mongodb://localhost:27017"
const val MONGO_DB = "mongobase"
