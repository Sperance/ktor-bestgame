package features.logic.locale

import application.enums.EnumModifierVariant
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect

/**
 * Текст модификатора из шаблонов эффектов (0.66.0).
 *
 * Словарь хранит не строку на каждое описание, а шаблон на пару «стат - операция»:
 * `stat.template.STOCK_HEALTH.ADD` = «+{v} к максимуму здоровья». Сервер на старте разворачивает
 * их в `modifier.<код>.name` для каждого описания, склеивая эффекты составного через
 * `stat.template.join`, и клиент по-прежнему читает готовую строку с плейсхолдерами `{0}`, `{1}`.
 *
 * Отрицательный эффект (все тиры не выше нуля) берёт шаблон `.negative` - «-{v} к максимуму
 * здоровья», а число в нём клиент печатает по модулю: плейсхолдер `{|0|}`. Конверсия берёт шаблон
 * `.per` с `{n}` (шаг источника) и `{s}` (подпись стата-источника).
 *
 * Своя строка у кода (`modifier.<код>.name`) или у семейства (`modifier.<семейство>.name`, на все
 * варианты) перекрывает сборку - для формулировок, которые из шаблона не сложить.
 */
object ModifierText {

    const val SECTION = "stat.template"
    const val JOIN = "$SECTION.join"
    private const val NEGATIVE = "negative"
    private const val PER = "per"

    /** Ключ шаблона эффекта [effect]; [negative] - для эффекта, чьи значения не выше нуля. */
    fun templateKey(effect: ModifierEffect, negative: Boolean): String {
        val base = "$SECTION.${(effect.stat as Enum<*>).name}.${effect.operation.name}"
        return when {
            effect.perStat != null -> "$base.$PER"
            negative -> "$base.$NEGATIVE"
            else -> base
        }
    }

    /** Эффект отрицателен, если ни один тир не поднимает его выше нуля, а хоть один опускает ниже. */
    fun negative(definition: ModifierDefinition, index: Int): Boolean {
        val ranges = definition.tiers.mapNotNull { it.values.getOrNull(index) }
        return ranges.isNotEmpty() && ranges.all { it[1] <= 0.0 } && ranges.any { it[0] < 0.0 }
    }

    /** Ключи словаря, которые нужны описанию: шаблон каждого эффекта, подпись источника конверсии, склейка. */
    fun keys(definition: ModifierDefinition): Set<String> = buildSet {
        definition.effects.forEachIndexed { index, effect ->
            add(templateKey(effect, negative(definition, index)))
            effect.perStat?.let { add(label(it)) }
        }
        if (definition.effects.size > 1) add(JOIN)
    }

    /** Ключи всех шаблонов, которые просят описания [definitions]. */
    fun keys(definitions: Collection<ModifierDefinition>): Set<String> = definitions.flatMapTo(mutableSetOf(), ::keys)

    /**
     * Строка описания по шаблонам из [strings], или null, если шаблона не хватает.
     */
    fun render(definition: ModifierDefinition, strings: Map<String, String>): String? {
        val parts = definition.effects.mapIndexed { index, effect ->
            val negative = negative(definition, index)
            val template = strings[templateKey(effect, negative)] ?: return null
            val value = if (negative) "{|$index|}" else "{$index}"
            var text = template.replace("{v}", value)
            effect.perStat?.let { source ->
                text = text.replace("{n}", number(effect.perAmount)).replace("{s}", strings[label(source)] ?: return null)
            }
            text
        }
        val join = if (parts.size > 1) strings[JOIN] ?: return null else ""
        return parts.joinToString(join)
    }

    /**
     * `modifier.<код>.name` для каждого описания, у которого в [strings] нет своей строки: строка
     * семейства, если она есть, иначе сборка по шаблонам. Описание, которое нечем собрать, пропускается -
     * тест локализации назовёт его по недостающему ключу.
     */
    fun generate(definitions: Collection<ModifierDefinition>, strings: Map<String, String>): Map<String, String> =
        definitions.mapNotNull { definition ->
            val key = LocaleKey.modifierName(definition.code)
            if (key in strings) return@mapNotNull null
            val family = LocaleKey.modifierName(definition.family).takeIf { definition.variant != EnumModifierVariant.NATURAL }?.let { strings[it] }
            (family ?: render(definition, strings))?.let { key to it }
        }.toMap()

    private fun label(stat: application.enums.IntEnumStat): String =
        LocaleKey.enumLabel((stat as Enum<*>)::class.simpleName.orEmpty(), stat.name)

    private fun number(value: Double): String =
        if (value == Math.floor(value)) value.toLong().toString() else value.toString()
}
