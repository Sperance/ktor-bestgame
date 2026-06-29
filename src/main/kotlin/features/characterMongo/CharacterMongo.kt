package features.characterMongo

import application.model.Stat
import extensions.ObjectIdSerializer
import extensions.now
import features.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class CharacterMongo(
    var userId: String,

    var name: String,
    var description: String = "",
    var level: Short = 1,
    var experience: Int = 0,
    var money: Long = 0,
    var params: MutableSet<Stat> = mutableSetOf(),
    var buffs: MutableSet<Stat> = mutableSetOf(),

    @Serializable(with = ObjectIdSerializer::class)
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity()

//@Serializable
//data class CharacterMongoResponse(
//    var userId: String,
//
//    var name: String,
//    var description: String,
//    var level: Short,
//    var experience: Int,
//    var money: ULong,
//    var params: MutableSet<Stat>,
//    var buffs: MutableSet<Stat>,
//
//    var id: String,
//    var version: Long,
//    var deleted: Boolean,
//    val createdAt: LocalDateTime,
//    val updatedAt: LocalDateTime,
//)
//
//// Функция-расширение для маппинга
//fun CharacterMongo.toResponse() = CharacterMongoResponse(
//    id = getId(),
//    userId = userId,
//    name = name,
//    description = description,
//    level = level,
//    experience = experience,
//    money = money,
//    params = params,
//    buffs = buffs,
//    version = version,
//    deleted = deleted,
//    createdAt = createdAt,
//    updatedAt = updatedAt
//)