package features.logic.locale

import application.enums.EnumRarity

/**
 * Ключи файлов локализации.
 *
 * Текста в документах Mongo нет: документ хранит код, а ключ собирается
 * из вида сущности и этого кода. Правила сборки лежат здесь, чтобы
 * сервер и клиент считали ключ одинаково, а тест мог сверить файлы
 * со справочниками.
 *
 * Формат плоский: `<раздел>.<КОД>.<поле>`, например
 * `equipment.IRON_SKULLCAP.name`.
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

    /** Английское имя того, чем торгуют (0.45.0): одно на все языки, для полной карточки и поиска. */
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

    fun regionName(code: String) = key(REGION, code, NAME)

    fun mapName(code: String) = key(MAP, code, NAME)
    fun mapDescription(code: String) = key(MAP, code, DESCRIPTION)

    fun monsterName(code: String) = key(MONSTER, code, NAME)

    fun professionName(code: String) = key(PROFESSION, code, NAME)
    fun professionDescription(code: String) = key(PROFESSION, code, DESCRIPTION)
    fun jobName(code: String) = key(JOB, code, NAME)

    /** Узел атласа (с 0.60.0): `atlas.node.<CODE>.name`; строки эффектов клиент собирает по характеристикам. */
    fun atlasNodeName(code: String) = key(ATLAS_NODE, code, NAME)

    /** Умение класса или монстра (с 0.69.0): `skill.<CODE>.name`; строки действия клиент собирает сам. */
    fun skillName(code: String) = key(SKILL, code, NAME)

    /** Ступень эссенции (с 0.69.0): `essence.tier.<CODE>`. */
    fun essenceTier(code: String) = "$ESSENCE.tier.$code"

    /** Имя стража кристалла по его эссенции (с 0.69.0): `essence.<ВИД>.monster` - «Ненасытный», «Ледяной». */
    fun essenceMonster(kind: String) = "$ESSENCE.$kind.monster"

    /**
     * Подпись значения перечисления: `enum.EnumRarity.UNIQUE`.
     */
    fun enumLabel(enumName: String, value: String) = "$ENUM.$enumName.$value"

    fun rarity(value: EnumRarity) = enumLabel("EnumRarity", value.name)

    /**
     * Сообщение об ошибке по её коду: `error.AU_002`.
     */
    fun error(code: String) = "$ERROR.$code"

    private fun key(section: String, code: String, field: String) = "$section.$code.$field"
}
