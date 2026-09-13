package ru.descend.features.character.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.domain.enums.EnumStatBattle
import ru.descend.domain.enums.EnumStatBool
import ru.descend.domain.enums.EnumStatProfession
import ru.descend.domain.enums.EnumStatStock
import ru.descend.domain.modifiers.Modifier
import ru.descend.features.character.model.CharacterBattleSkill
import ru.descend.features.character.model.CharacterBoolSkill
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.CharacterItems
import ru.descend.features.character.model.CharacterProfessionSkill
import ru.descend.features.character.model.CharacterStockSkill
import ru.descend.features.character.model.GainedRedemtionCodes
import ru.descend.shared.extensions.now
import ru.descend.shared.model.VersionedEntity

@Serializable
@kotlinx.serialization.SerialName("features.data.character.Character")
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
