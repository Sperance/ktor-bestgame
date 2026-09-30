package com.sperance.exileforge.rules.text

import com.sperance.exileforge.rules.content.Condition
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildMode
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.StatDef
import com.sperance.exileforge.rules.content.StatGroup

/**
 * Ключи словаря: `<раздел>.<КОД>.<поле>`. Текста в контенте и документах нет - у сущности код, а
 * строка лежит в словаре под ключом, который обе стороны собирают здесь одинаково.
 */
object LocaleKey {
    const val EQUIPMENT = "equipment"
    const val ITEM = "item"
    const val MODIFIER = "modifier"
    const val SKILL_NODE = "skilltree"
    const val CHARACTER_CLASS = "class"
    const val ENUM = "enum"
    const val ERROR = "error"
    const val CURRENCY = "currency"
    const val REGION = "region"
    const val MAP = "map"
    const val MONSTER = "monster"
    const val PROFESSION = "profession"
    const val JOB = "job"
    const val ATLAS_NODE = "atlas.node"
    const val SKILL = "skill"
    const val ESSENCE = "essence"
    const val GUILD = "guild"
    const val QUEST = "quest"
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val TRADE = "trade"

    fun equipmentName(code: String) = key(EQUIPMENT, code, NAME)
    /** Имя карты с её зоной (1.43.0): «{0} Map», где {0} - [mapName] зоны. */
    fun mapItemName() = key(EQUIPMENT, com.sperance.exileforge.rules.content.MAP_TEMPLATE, "zone")
    fun equipmentTrade(code: String) = key(EQUIPMENT, code, TRADE)
    /** Лор уникального и мифического предмета; у баз (обычных, волшебных, редких шаблонов) описания нет. */
    fun equipmentDescription(code: String) = key(EQUIPMENT, code, DESCRIPTION)
    fun itemName(code: String) = key(ITEM, code, NAME)
    fun itemTrade(code: String) = key(ITEM, code, TRADE)
    fun itemDescription(code: String) = key(ITEM, code, DESCRIPTION)
    fun modifierName(code: String) = key(MODIFIER, code, NAME)
    /** Хвост строки условного эффекта (1.34.0): «на низком здоровье». */
    fun condition(condition: Condition) = "condition.${condition.name}"
    fun skillNodeName(code: String) = key(SKILL_NODE, code, NAME)
    fun skillNodeDescription(code: String) = key(SKILL_NODE, code, DESCRIPTION)
    fun className(code: String) = key(CHARACTER_CLASS, code, NAME)
    fun classDescription(code: String) = key(CHARACTER_CLASS, code, DESCRIPTION)
    /** Текст экрана выбора класса: [ClassText] - роль, история, стиль, сильные и слабые стороны, архетипы. */
    fun classText(code: String, field: ClassText) = key(CHARACTER_CLASS, code, field.key)
    fun regionName(code: String) = key(REGION, code, NAME)
    fun mapName(code: String) = key(MAP, code, NAME)
    fun mapDescription(code: String) = key(MAP, code, DESCRIPTION)
    fun monsterName(code: String) = key(MONSTER, code, NAME)
    fun professionName(code: String) = key(PROFESSION, code, NAME)
    fun professionDescription(code: String) = key(PROFESSION, code, DESCRIPTION)
    fun jobName(code: String) = key(JOB, code, NAME)
    /** Выбор работы, что не предмет (1.44.0): группа и атрибут кузнеца. */
    fun choiceName(code: String) = key("choice", code, NAME)
    fun atlasNodeName(code: String) = key(ATLAS_NODE, code, NAME)
    fun skillName(code: String) = key(SKILL, code, NAME)
    fun essenceTier(code: String) = "$ESSENCE.tier.$code"
    fun essenceMonster(kind: String) = "$ESSENCE.$kind.monster"
    fun enumLabel(enumName: String, value: String) = "$ENUM.$enumName.$value"
    fun rarity(value: Rarity) = enumLabel("EnumRarity", value.name)
    fun error(code: String) = "$ERROR.$code"
    fun guildFactionName(code: String) = key("$GUILD.faction", code, NAME)
    fun guildFactionDescription(code: String) = key("$GUILD.faction", code, DESCRIPTION)
    fun guildRank(code: String) = "$GUILD.rank.$code"
    fun guildRole(role: GuildRole) = "$GUILD.role.${role.name}"
    fun guildMode(mode: GuildMode) = "$GUILD.mode.${mode.name}"
    fun guildEmblem(code: String) = "$GUILD.emblem.$code"
    fun guildLog(kind: GuildLogKind) = "$GUILD.log.${kind.name}"

    /** Название задания: шаг сюжета - `quest.story.<код>.name`, остальные - шаблон цели `quest.goal.<код>`. */
    fun questTitle(kind: QuestKind, goal: String) = if (kind == QuestKind.STORY) key("$QUEST.story", goal, NAME) else "$QUEST.goal.$goal"

    /** Подпись характеристики: прежние перечисления - по группе реестра. */
    fun statLabel(stat: StatDef): String = enumLabel(when (stat.group) {
        StatGroup.BOOL -> "EnumStatBool"
        StatGroup.PROFESSION -> "EnumStatProfession"
        StatGroup.BATTLE -> "EnumStatBattle"
        else -> "EnumStatStock"
    }, stat.code)

    private fun key(section: String, code: String, field: String) = "$section.$code.$field"
}

/** Поля описания класса на экране выбора; списки (сильные и слабые стороны) - строки через перевод строки. */
enum class ClassText(val key: String) { ROLE("role"), LORE("lore"), STYLE("style"), PROS("pros"), CONS("cons"), BUILDS("builds") }
