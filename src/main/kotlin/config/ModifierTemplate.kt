package config

import application.enums.EnumInfluence
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

    /**
     * Стат-источник конверсии, см. [ModifierEffect.perStat].
     */
    val perStat: IntEnumStat? = null,

    /**
     * Сколько единиц источника дают одно значение эффекта.
     */
    val perAmount: Double = 1.0,
)

fun effect(
    stat: IntEnumStat,
    operation: EnumModifierOperation,
    best: ClosedFloatingPointRange<Double>,
    worst: ClosedFloatingPointRange<Double> = best,
) = EffectTemplate(stat, operation, best, worst)

/**
 * Эффект-конверсия: "[value] к [stat] за каждые [perAmount] единиц [perStat]".
 *
 * Значение фиксировано - у конверсии меняется не оно, а стат-источник,
 * поэтому тиров у такого эффекта не бывает.
 */
fun conversion(
    stat: IntEnumStat,
    operation: EnumModifierOperation,
    perStat: IntEnumStat,
    perAmount: Double,
    value: Double = 1.0,
) = EffectTemplate(stat, operation, value..value, value..value, perStat, perAmount)

/**
 * Один тир из таблицы модификаторов POE: с какого item level он открывается и какие
 * у него диапазоны - по одному на эффект, в порядке эффектов.
 */
data class TierRow(val itemLevel: Int, val values: List<ClosedFloatingPointRange<Double>>)

fun tier(itemLevel: Int, vararg values: ClosedFloatingPointRange<Double>) = TierRow(itemLevel, values.toList())

/**
 * Шаблон модификатора по таблице тиров (с 0.36.0): тир 1 - первая строка, у каждой свой
 * item level и свои диапазоны, ровно как в POE, без интерполяции между краями.
 */
fun tabled(
    code: String,
    source: EnumModifierSource,
    stats: List<Pair<IntEnumStat, EnumModifierOperation>>,
    tiers: List<TierRow>,
    tags: List<String> = emptyList(),
    isLocal: Boolean = false,
    group: String? = null,
    weight: Int = ModifierDefinition.DEFAULT_SPAWN_WEIGHT,
): ModifierTemplate {
    require(tiers.isNotEmpty() && tiers.all { it.values.size == stats.size }) { "$code: a tier per row, a range per effect" }
    return ModifierTemplate(
        code = code,
        source = source,
        effects = stats.mapIndexed { index, (stat, operation) -> effect(stat, operation, tiers.first().values[index], tiers.last().values[index]) },
        tierCount = tiers.size,
        bestItemLevel = tiers.first().itemLevel,
        worstItemLevel = tiers.last().itemLevel,
        tags = tags,
        isLocal = isLocal,
        group = group,
        weight = weight,
        table = tiers,
    )
}

/**
 * Шаблон модификатора для сидера: описание плюс границы его тиров.
 *
 * Несколько эффектов = составной (гибридный) модификатор,
 * который меняет сразу несколько статов.
 */
data class ModifierTemplate(
    val code: String,
    val source: EnumModifierSource,
    val effects: List<EffectTemplate>,
    val tierCount: Int,
    val bestItemLevel: Int,
    val worstItemLevel: Int = 1,
    val tags: List<String> = emptyList(),

    /**
     * Локальный модификатор считается внутри своего предмета,
     * см. [ModifierDefinition.isLocal].
     */
    val isLocal: Boolean = false,

    /**
     * Группа, см. [ModifierDefinition.group]. null - своя, по коду.
     */
    val group: String? = null,

    /**
     * Вес в пуле, см. [ModifierDefinition.spawnWeight].
     */
    val weight: Int = ModifierDefinition.DEFAULT_SPAWN_WEIGHT,

    /**
     * Влияние, которое открывает модификатор, см. [ModifierDefinition.influence].
     */
    val influence: EnumInfluence? = null,

    /**
     * Ремесленный модификатор верстака, см. [ModifierDefinition.crafted].
     */
    val crafted: Boolean = false,

    /**
     * Явная таблица тиров, см. [tabled]. Пустая - тиры интерполируются между краями.
     */
    val table: List<TierRow> = emptyList(),
) {

    fun toDefinition() = ModifierDefinition(
        code = code,
        effects = effects.map { ModifierEffect(it.stat, it.operation, it.perStat, it.perAmount) },
        source = source,
        isLocal = isLocal,
        tags = tags.toMutableList(),
        group = group,
        spawnWeight = weight,
        influence = influence,
        crafted = crafted,
        // Справочник пересевается на каждом старте, поэтому _id должен быть
        // стабильным: иначе зароленные модификаторы предметов потеряют ссылки
        _id = code.toStableObjectId()
    )

    /**
     * Разворачивает тиры модификатора между лучшим (тир 1) и худшим (тир [tierCount]).
     *
     * Значения каждого эффекта и требуемый item level интерполируются линейно,
     * так что достаточно задать только границы, как в таблицах модификаторов POE.
     * Шаблон с [table] берёт тиры из неё как есть.
     */
    fun toTiers(modifierId: String): List<ModifierTier> = if (table.isNotEmpty()) table.mapIndexed { index, row ->
        ModifierTier(
            modifierId = modifierId,
            tier = index + 1,
            _id = "$code#${index + 1}".toStableObjectId(),
            values = row.values.map { ModifierTierValue(valueMin = it.start, valueMax = it.endInclusive) },
            minItemLevel = row.itemLevel,
            weight = index + 1
        )
    } else (1..tierCount).map { tier ->
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
