package ru.descend.features.character.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.shared.extensions.now
import ru.descend.shared.model.VersionedEntity

/**
 * Одна принадлежащая персонажу единица предмета — один документ Mongo.
 *
 * Стаков нет: поля `amount` в модели не существует. Тысяча хаос-орбов — это тысяча документов
 * со своими идентификаторами, а не строка `{itemId, amount: 1000}` внутри персонажа. Списание —
 * удаление нужного числа документов, баланс — подсчёт документов по `(characterId, itemId)`.
 *
 * Размер документа персонажа от количества предметов не зависит, а сам список ограничен только
 * объёмом коллекции.
 */
@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterInventoryItem")
data class CharacterInventoryItem(
    var characterId: String,
    var itemId: String,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity
