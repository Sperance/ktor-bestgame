package ru.descend.features.character.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ru.descend.shared.extensions.now
import ru.descend.shared.model.VersionedEntity

/**
 * Один предмет экипировки — один документ Mongo.
 *
 * Раньше весь инвентарь лежал массивом внутри Character, поэтому пары десятков предметов
 * со снимком базы и роллами уже приближали документ к лимиту в 16 МБ. Теперь размер
 * персонажа не зависит от количества предметов: список ограничен только объёмом коллекции,
 * то есть практически бесконечен.
 *
 * [item] — ровно та же структура, что хранилась в массиве, поэтому логика модификаторов
 * (роллы, крафт, расчёт статов) читает её без изменений.
 */
@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterEquipmentItem")
data class CharacterEquipmentItem(
    var characterId: String,
    var item: CharacterEquipments,

    /** Совпадает с [CharacterEquipments.uuid]: предмет остаётся адресуемым по прежнему идентификатору. */
    override var _id: String = item.uuid,
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity
