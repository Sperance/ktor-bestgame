package features.data.character

import application.enums.EnumCharacterSkills
import extensions.now
import base.entity.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Character(
    var userId: String,

    var name: String,
    var description: String = "",
    var level: Short = 1,
    var experience: Int = 0,
    var money: Long = 0,
    var params: MutableList<ModificationValue> = mutableListOf(),
    var equipments: MutableList<CharacterEquipments> = mutableListOf(),
    var items: MutableList<CharacterItems> = mutableListOf(),
    var professionSkills: MutableList<CharacterProfessionSkill> = mutableListOf(),
    var recipeAccess: MutableList<String> = mutableListOf(),

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {
    fun getProfessionSkill(skill: EnumCharacterSkills) : CharacterProfessionSkill {
        return professionSkills.find { it.stat == skill } ?: CharacterProfessionSkill(skill, 0)
    }
}

@Serializable
data class CharacterProfessionSkill(
    val stat: EnumCharacterSkills,
    val level: Byte,
    val experience: Double = 0.0
)

@Serializable
data class CharacterItems(
    var itemId: String,
    var amount: Long
)

@Serializable
data class CharacterEquipments(
    var equipmentId: String,
    var equipmentType: String, // "weapon", "armor", "accessory"
    var params: List<ModificationValue> = listOf(),

    var uuid: String = ObjectId().toHexString(),
)

@Serializable
data class ModificationValue(
    var property_id: String,
    var level: Byte,
    var power: Double
)