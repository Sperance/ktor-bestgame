package com.sperance.exileforge.rules.text

import com.sperance.exileforge.rules.content.Effect
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.StatRegistry
import com.sperance.exileforge.rules.content.VariantKind

/**
 * Текст модификатора из шаблонов эффектов: словарь хранит шаблон на пару «стат - операция»
 * (`stat.template.STOCK_HEALTH.ADD` = «+{v} к максимуму здоровья»), а строка описания собирается
 * из них на обеих сторонах: `{0}`, `{1}` - плейсхолдеры значений, `{|0|}` - модуль отрицательного.
 *
 * Своя строка кода (`modifier.<код>.name`) или семейства перекрывает сборку. Стат без шаблона берёт
 * общий `stat.template.generic.<OP>` с подписью стата `{s}` - так ни одна строка не остаётся без текста.
 */
class ModifierText(private val stats: StatRegistry, private val strings: (String) -> String?) {

    /** Строка описания с плейсхолдерами; null - словарю не хватает и общего шаблона. */
    fun template(def: ModifierDef): String? {
        strings(LocaleKey.modifierName(def.code))?.let { return it }
        if (def.variant != VariantKind.NATURAL) strings(LocaleKey.modifierName(def.family))?.let { return it }
        return render(def)
    }

    fun render(def: ModifierDef): String? {
        val parts = def.effects.mapIndexed { index, effect ->
            val negative = negative(def, index)
            val value = if (negative) "{|$index|}" else "{$index}"
            var text = strings(templateKey(effect, negative)) ?: generic(effect) ?: return null
            text = text.replace("{v}", value)
            effect.perStat?.let { source -> text = text.replace("{n}", number(effect.perAmount)).replace("{s}", label(source) ?: return null) }
            text
        }
        val join = if (parts.size > 1) strings(JOIN) ?: return null else ""
        return parts.joinToString(join)
    }

    /** Готовая строка с подставленными значениями [values]. */
    fun line(def: ModifierDef, values: List<Double>): String? = template(def)?.let { fill(it, values) }

    private fun generic(effect: Effect): String? {
        val template = strings("$SECTION.$GENERIC.${effect.op.name}") ?: return null
        return template.replace("{s}", label(effect.stat) ?: effect.stat)
    }

    private fun label(stat: String): String? = stats[stat]?.let { strings(LocaleKey.statLabel(it)) }

    companion object {
        const val SECTION = "stat.template"
        const val JOIN = "$SECTION.join"
        const val GENERIC = "generic"

        fun templateKey(effect: Effect, negative: Boolean): String {
            val base = "$SECTION.${effect.stat}.${effect.op.name}"
            return when {
                effect.perStat != null -> "$base.per"
                negative -> "$base.negative"
                else -> base
            }
        }

        /** Эффект отрицателен, если ни один тир не поднимает его выше нуля, а хоть один опускает ниже. */
        fun negative(def: ModifierDef, index: Int): Boolean {
            val ranges = def.tiers.mapNotNull { it.values.getOrNull(index) }
            return ranges.isNotEmpty() && ranges.all { it[1] <= 0.0 } && ranges.any { it[0] < 0.0 }
        }

        /** Подстановка значений: `{0}` - число, `{|0|}` - его модуль; целое печатается без дроби. */
        fun fill(template: String, values: List<Double>): String {
            var text = template
            values.forEachIndexed { index, value ->
                text = text.replace("{|$index|}", number(Math.abs(value))).replace("{$index}", number(value))
            }
            return text
        }

        fun number(value: Double): String = if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
    }
}
