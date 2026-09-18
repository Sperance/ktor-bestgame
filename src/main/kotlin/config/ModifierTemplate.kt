package config

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import extensions.to1Digits
import extensions.toStableObjectId
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ModifierTierValue
import kotlin.math.roundToInt

/**
 * Один эффект модификатора с границами значений между лучшим и худшим тиром.
 *
 * Для модификатора с единственным тиром (implicit уникалок, коррапт)
 * достаточно задать только [best].
 */
data class EffectTemplate(
    val stat: IntEnumStat,
    val operation: EnumModifierOperation,
    val best: ClosedFloatingPointRange<Double>,
    val worst: ClosedFloatingPointRange<Double> = best,
)

fun effect(
    stat: IntEnumStat,
    operation: EnumModifierOperation,
    best: ClosedFloatingPointRange<Double>,
    worst: ClosedFloatingPointRange<Double> = best,
) = EffectTemplate(stat, operation, best, worst)

/**
 * Шаблон модификатора для сидера: описание плюс границы его тиров.
 *
 * Несколько эффектов = составной (гибридный) модификатор,
 * который меняет сразу несколько статов.
 */
data class ModifierTemplate(
    val code: String,
    val name: String,
    val source: EnumModifierSource,
    val effects: List<EffectTemplate>,
    val tierCount: Int,
    val bestItemLevel: Int,
    val worstItemLevel: Int = 1,
    val tags: List<String> = emptyList(),
) {

    fun toDefinition() = ModifierDefinition(
        code = code,
        effects = effects.map { ModifierEffect(it.stat, it.operation) },
        source = source,
        name = name,
        tags = tags.toMutableList(),
        // Справочник пересевается на каждом старте, поэтому _id должен быть
        // стабильным: иначе зароленные модификаторы предметов потеряют ссылки
        _id = code.toStableObjectId()
    )

    /**
     * Разворачивает тиры модификатора между лучшим (тир 1) и худшим (тир [tierCount]).
     *
     * Значения каждого эффекта и требуемый item level интерполируются линейно,
     * так что достаточно задать только границы, как в таблицах модификаторов POE.
     */
    fun toTiers(modifierId: String): List<ModifierTier> = (1..tierCount).map { tier ->
        val progress = if (tierCount == 1) 0.0 else (tier - 1).toDouble() / (tierCount - 1).toDouble()

        ModifierTier(
            modifierId = modifierId,
            tier = tier,
            _id = "$code#$tier".toStableObjectId(),
            values = effects.map { effect ->
                ModifierTierValue(
                    valueMin = lerp(effect.best.start, effect.worst.start, progress),
                    valueMax = lerp(effect.best.endInclusive, effect.worst.endInclusive, progress)
                )
            },
            minItemLevel = lerp(bestItemLevel.toDouble(), worstItemLevel.toDouble(), progress)
                .roundToInt()
                .coerceAtLeast(1),
            // Чем лучше тир, тем реже он выпадает среди доступных
            weight = tier
        )
    }

    private fun lerp(from: Double, to: Double, progress: Double) = (from + (to - from) * progress).to1Digits()
}

/**
 * Документы описаний для набора шаблонов.
 */
fun List<ModifierTemplate>.toDefinitions(): List<ModifierDefinition> = map { it.toDefinition() }

/**
 * Документы тиров для тех описаний, которые принадлежат этому набору шаблонов.
 *
 * Описания с чужими кодами пропускаются, поэтому в [definitions]
 * можно передавать всю коллекцию целиком.
 */
fun List<ModifierTemplate>.toTiers(definitions: List<ModifierDefinition>): List<ModifierTier> {
    val byCode = associateBy { it.code }
    return definitions.flatMap { definition ->
        byCode[definition.code]?.toTiers(definition._id) ?: emptyList()
    }
}
