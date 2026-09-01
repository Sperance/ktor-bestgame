package features.data.character

import application.enums.EnumStatBattle
import application.enums.EnumStatBool
import application.enums.EnumStatProfession
import application.enums.EnumStatStock
import extensions.now
import base.entity.VersionedEntity
import features.data.character.character_data.CharacterBattleSkill
import features.data.character.character_data.CharacterBoolSkill
import features.data.character.character_data.CharacterEquipments
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.CharacterProfessionSkill
import features.data.character.character_data.CharacterStockSkill
import features.data.character.character_data.GainedRedemtionCodes
import features.logic.modifiers.Modifier
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Character(
    var userId: String,

    var name: String,
    var description: String = "",
    var level: Short = 1,
    var experience: Double = 0.0,
    var money: Long = 0,
    var params: MutableList<Modifier> = mutableListOf(),
    var equipments: MutableList<CharacterEquipments> = mutableListOf(),
    var items: MutableList<CharacterItems> = mutableListOf(),
    var professionSkills: MutableList<CharacterProfessionSkill> = mutableListOf(),
    var stockSkills: MutableList<CharacterStockSkill> = mutableListOf(),
    var battleSkills: MutableList<CharacterBattleSkill> = mutableListOf(),
    var boolSkills: MutableList<CharacterBoolSkill> = mutableListOf(),
    var recipeAccess: MutableList<String> = mutableListOf(),
    var gainedRedemptionCodes: MutableList<GainedRedemtionCodes> = mutableListOf(),

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {
    fun getProfessionSkill(skill: EnumStatProfession) : CharacterProfessionSkill {
        return professionSkills.find { it.stat == skill } ?: CharacterProfessionSkill(skill, 0)
    }
    fun getStockSkill(skill: EnumStatStock) : CharacterStockSkill {
        return stockSkills.find { it.stat == skill } ?: CharacterStockSkill(skill, 0)
    }
    fun getBattleSkill(skill: EnumStatBattle) : CharacterBattleSkill {
        return battleSkills.find { it.stat == skill } ?: CharacterBattleSkill(skill, 0)
    }
    fun getBoolSkill(skill: EnumStatBool) : CharacterBoolSkill {
        return boolSkills.find { it.stat == skill } ?: CharacterBoolSkill(skill, null)
    }
}