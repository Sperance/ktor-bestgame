package features.property

import application.enums.EnumStatHelper

object PropertyCache {
    private var properties = listOf<Property>()

    /** Обратный маппинг: id → EnumStatHelper (для расчёта статов) */
    private var idToEnum = mapOf<Long, EnumStatHelper>()

    fun refresh(repo: PropertyRepository) {
        properties = repo.findAll()
        idToEnum = properties.mapNotNull { prop ->
            val enum = EnumStatHelper.entries.find { it.name == prop.code }
            if (enum != null) prop.id to enum else null
        }.toMap()
    }

    fun getAll() = properties

    fun getIdFromEnum(enum: EnumStatHelper) =
        properties.find { it.code == enum.name }!!.id

    /** Найти EnumStatHelper по id из БД (null если не найден) */
    fun getEnumFromId(id: Long): EnumStatHelper? = idToEnum[id]

    /** Все маппинги id → enum (для StatCalculationService) */
    fun getIdToEnumMap(): Map<Long, EnumStatHelper> = idToEnum
}
