package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/**
 * Что свойство монстра делает в бою само (1.69.0), сверх строк на листе:
 * [BURST] - павший бьёт героя долей [TraitTrigger.value] своего здоровья в стихии [TraitTrigger.element];
 * [ENRAGE] - ниже [TraitTrigger.threshold]% здоровья берёт строки [TraitTrigger.lines] до конца боя;
 * [FIRST_STRIKE] - первый удар монстра сильнее на [TraitTrigger.value]%;
 * [RALLY] - павший даёт стоящим союзникам строки [TraitTrigger.lines] на [TraitTrigger.duration] секунд;
 * [MEND] - павший лечит стоящих союзников на [TraitTrigger.value]% их здоровья.
 */
@Serializable
enum class TraitAct { BURST, ENRAGE, FIRST_STRIKE, RALLY, MEND }

/** Строка свойства: стат, операция и значение; всё, кроме [Op.SET], растёт с силой редкости. */
@Serializable
data class TraitLine(val stat: String, val op: Op = Op.ADD, val value: Double)

@Serializable
data class TraitTrigger(
    val act: TraitAct,
    val value: Double = 0.0,
    val threshold: Double = 0.0,
    val element: String? = null,
    val lines: List<TraitLine> = emptyList(),
    val duration: Double = 0.0,
)

/**
 * Свойство монстра (1.69.0): строки на лист [lines], боевой отклик [trigger] и навык [skill] (код умения монстра).
 * Название и описание - в словаре `trait.<code>.name|description`.
 */
@Serializable
data class MonsterTrait(
    val code: String,
    val icon: String,
    val lines: List<TraitLine> = emptyList(),
    val trigger: TraitTrigger? = null,
    val skill: String? = null,
)

/**
 * Свойства монстров (1.69.0): [list] - все; [forms] - свойство каждой формы; своё свойство типа - поле `trait` монстра.
 * [power] - сила свойств по редкости; у боссов и стражей своих свойств нет.
 */
@Serializable
data class TraitRules(
    val list: List<MonsterTrait> = emptyList(),
    val forms: Map<String, String> = emptyMap(),
    val power: Map<MonsterRarity, Double> = emptyMap(),
) {
    val byCode: Map<String, MonsterTrait> by lazy { list.associateBy { it.code } }

    fun power(rarity: MonsterRarity): Double = power[rarity] ?: 1.0

    /** Свойства монстра: формы, затем своё; у босса и порченого стража - ни одного. */
    fun of(monster: Monster): List<MonsterTrait> = if (monster.boss || monster.corrupted) {
        emptyList()
    } else {
        listOfNotNull(forms[monster.form], monster.trait).distinct().mapNotNull(byCode::get)
    }

    /** Значение [value] строки или отклика на силе [power]; [Op.SET] - как есть. */
    fun scaled(line: TraitLine, power: Double): Double = if (line.op == Op.SET) line.value else line.value * power
}
