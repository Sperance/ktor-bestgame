package ru.descend.exileforge.base.entity
import org.bson.BsonValue

/**
 * Сущность, которая помнит свой документ, каким его прочли или последний раз записали (1.1.0): полная
 * запись [ru.descend.exileforge.base.repository.BaseRepository.update] шлёт `$set` только полям, чьё значение с тех пор
 * изменилось. Большой документ - герой с тайником - не переписывается целиком ради одной сферы.
 * Память - поле объекта, помеченное `@Transient`: в базу и на провод она не уходит.
 */
interface TrackedEntity : StockEntity {
    var loaded: Map<String, BsonValue>?
}
