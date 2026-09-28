package com.sperance.exileforge.rules.text

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
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val TRADE = "trade"

    fun equipmentName(code: String) = key(EQUIPMENT, code, NAME)
    fun equipmentTrade(code: String) = key(EQUIPMENT, code, TRADE)
    fun equipmentDescription(code: String) = key(EQUIPMENT, code, DESCRIPTION)
    fun itemName(code: String) = key(ITEM, code, NAME)
    fun itemTrade(code: String) = key(ITEM, code, TRADE)
    fun itemDescription(code: String) = key(ITEM, code, DESCRIPTION)
    fun modifierName(code: String) = key(MODIFIER, code, NAME)
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
    fun atlasNodeName(code: String) = key(ATLAS_NODE, code, NAME)
    fun skillName(code: String) = key(SKILL, code, NAME)
    fun essenceTier(code: String) = "$ESSENCE.tier.$code"
    fun essenceMonster(kind: String) = "$ESSENCE.$kind.monster"
    fun enumLabel(enumName: String, value: String) = "$ENUM.$enumName.$value"
    fun rarity(value: Rarity) = enumLabel("EnumRarity", value.name)
    fun error(code: String) = "$ERROR.$code"

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
