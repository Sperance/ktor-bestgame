package config

import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import extensions.toStableObjectId
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ModifierTierValue
import kotlinx.serialization.Serializable

/**
 * Один эффект модификатора в файле: стат, операция и, для конверсии, её источник.
 */
@Serializable
data class EffectRecord(
    val stat: IntEnumStat,
    val operation: EnumModifierOperation,
    val perStat: IntEnumStat? = null,
    val perAmount: Double = 1.0,
)

/**
 * Один тир из таблицы модификаторов POE: с какого item level он открывается и какие
 * у него диапазоны - по одному `[min, max]` на эффект, в порядке эффектов.
 */
@Serializable
data class TierRecord(val level: Int, val values: List<List<Double>>)

/**
 * Модификатор в том виде, в каком он лежит в `content/modifiers.json` (с 0.39.0).
 *
 * Тир 1 - первая строка таблицы, у каждой свой item level и свои диапазоны, ровно как в POE.
 * Несколько эффектов = составной (гибридный) модификатор. [pools] - в каких пулах он
 * состоит и с каким весом, см. [ModifierDefinition.pools].
 */
@Serializable
data class ModifierRecord(
    val code: String,
    val source: EnumModifierSource,
    val effects: List<EffectRecord>,
    val tiers: List<TierRecord>,
    val tags: List<String> = emptyList(),
    val local: Boolean = false,
    val group: String? = null,
    val influence: EnumInfluence? = null,
    val crafted: Boolean = false,
    val pools: Map<String, Int> = emptyMap(),
) {

    fun toDefinition() = ModifierDefinition(
        code = code,
        effects = effects.map { ModifierEffect(it.stat, it.operation, it.perStat, it.perAmount) },
        source = source,
        isLocal = local,
        tags = tags.toMutableList(),
        group = group,
        pools = pools,
        influence = influence,
        crafted = crafted,
        // Справочник пересевается на каждом старте, поэтому _id должен быть
        // стабильным: иначе зароленные модификаторы предметов потеряют ссылки
        _id = code.toStableObjectId()
    )

    /** Тиры по таблице; чем лучше тир, тем реже он выпадает среди доступных. */
    fun toTiers(modifierId: String): List<ModifierTier> = tiers.mapIndexed { index, row ->
        ModifierTier(
            modifierId = modifierId,
            tier = index + 1,
            _id = "$code#${index + 1}".toStableObjectId(),
            values = row.values.map { (min, max) -> ModifierTierValue(valueMin = min, valueMax = max) },
            minItemLevel = row.level,
            weight = index + 1
        )
    }

    /** Ошибка файла, названная по коду, или null. */
    fun problem(): String? = when {
        tiers.isEmpty() -> "$code: no tiers"
        tiers.any { row -> row.values.size != effects.size || row.values.any { it.size != 2 || it[0] > it[1] } } -> "$code: a [min, max] per effect in every tier"
        pools.any { (tag, weight) -> tag.isBlank() || weight < 0 } -> "$code: pools"
        else -> null
    }
}

/**
 * Документы описаний для набора записей.
 */
fun List<ModifierRecord>.toDefinitions(): List<ModifierDefinition> = map { it.toDefinition() }

/**
 * Документы тиров для тех описаний, которые принадлежат этому набору записей.
 *
 * Описания с чужими кодами пропускаются, поэтому в [definitions]
 * можно передавать всю коллекцию целиком.
 */
fun List<ModifierRecord>.toTiers(definitions: List<ModifierDefinition>): List<ModifierTier> {
    val byCode = associateBy { it.code }
    return definitions.flatMap { definition ->
        byCode[definition.code]?.toTiers(definition._id) ?: emptyList()
    }
}
