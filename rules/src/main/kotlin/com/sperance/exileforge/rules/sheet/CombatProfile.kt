package com.sperance.exileforge.rules.sheet

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.CoreStat
import com.sperance.exileforge.rules.content.GenericDamage

/**
 * Боевой профиль листа (1.63.0): урон в секунду раздельно для атаки, заклинания и недугов. Раньше одна оценка брала
 * удары атаки со скоростью атаки и лучший из критов атак и чар - крит чар доставался удару атаки. Теперь каждый профиль
 * считается своими строками, а оценка героя - сильнейший из них: журнал не говорит, чем убито. Общий для сервера
 * (сверка темпа захода) и симуляции экономики.
 */
data class CombatProfile(val attack: Double, val spell: Double, val ailment: Double) {
    /** Сильнейший профиль - верхняя честная оценка урона героя. */
    val best: Double get() = maxOf(attack, spell, ailment).coerceAtLeast(1.0)

    companion object {
        private const val SPELL_ADD = "STOCK_SPELL_ADD_"
        private val AILMENT_DAMAGE = listOf("STOCK_BURNING_DAMAGE", "STOCK_POISON_DAMAGE", "STOCK_BLEED_DAMAGE")

        fun of(index: ContentIndex, sheet: Map<String, Double>): CombatProfile {
            val combat = index.campaign.combat
            val critical = combat.critical
            fun stat(code: String) = (sheet[code] ?: 0.0).coerceAtLeast(0.0)
            val hit = GenericDamage.HITS.sumOf(::stat).takeIf { it > 0 } ?: combat.unarmed.damage
            val speed = (sheet["STOCK_ATTACK_SPEED"] ?: 0.0).takeIf { it > 0 }?.coerceIn(combat.attackSpeedMin, combat.attackSpeedMax) ?: combat.unarmed.speed
            val damage = sheet[CoreStat.CRITICAL_DAMAGE.code]
            val attackCrit = critFactor(
                sheet[CoreStat.CRITICAL_CHANCE.code] ?: critical.chance,
                critical.effective(sheet[CoreStat.CRITICAL_MULTIPLIER.code] ?: critical.multiplier, damage),
            )
            val spellCrit = critFactor(
                sheet[CoreStat.SPELL_CRITICAL_CHANCE.code] ?: critical.spellChance,
                critical.effective(sheet[CoreStat.SPELL_CRITICAL_MULTIPLIER.code] ?: critical.spellMultiplier, damage),
            )
            val attack = hit * speed * attackCrit
            // Заклинание: удар с прибавками чар, темп - скорость атаки с ускорением чар, крит - свой
            val spellHit = hit + sheet.filterKeys { it.startsWith(SPELL_ADD) }.values.sumOf { it.coerceAtLeast(0.0) }
            val cast = speed * (1 + stat("STOCK_CAST_SPEED") / 100)
            val spell = spellHit * cast * spellCrit
            // Недуги: доля урона сильнейшего профиля удара, усиленная лучшим увеличением урона недугов
            val ailment = maxOf(attack, spell) * combat.ailmentShare * (1 + AILMENT_DAMAGE.maxOf(::stat) / 100)
            return CombatProfile(attack.coerceAtLeast(1.0), spell.coerceAtLeast(1.0), ailment)
        }

        /** Ожидаемый множитель урона от крита: шанс и множитель в процентах. */
        private fun critFactor(chance: Double, multiplier: Double): Double = 1 + (chance / 100).coerceIn(0.0, 1.0) * (multiplier.coerceAtLeast(100.0) / 100 - 1)
    }
}
