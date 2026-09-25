package config

import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import extensions.toStableObjectId
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierTier
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
 * Модификатор в том виде, в каком он лежит в `content/modifiers.json` (с 0.39.0).
 *
 * Тир 1 - первая строка таблицы, у каждой свой item level и свои диапазоны, ровно как в POE;
 * с 0.56.0 тиры так и лежат внутри описания. Несколько эффектов = составной (гибридный)
 * модификатор. В каких пулах он состоит, говорит `content/pools.json`, см. [PoolSeeder].
 */
@Serializable
data class ModifierRecord(
    val code: String,
    val source: EnumModifierSource,
    val effects: List<EffectRecord>,
    val tiers: List<ModifierTier>,
    val tags: List<String> = emptyList(),
    val local: Boolean = false,
    val group: String? = null,
    val influence: EnumInfluence? = null,
    val crafted: Boolean = false,
) {

    fun toDefinition() = ModifierDefinition(
        code = code,
        effects = effects.map { ModifierEffect(it.stat, it.operation, it.perStat, it.perAmount) },
        source = source,
        tiers = tiers,
        isLocal = local,
        tags = tags.toMutableList(),
        group = group,
        influence = influence,
        crafted = crafted,
        _id = code.toStableObjectId()
    )

    /** Ошибка файла, названная по коду, или null. */
    fun problem(): String? = when {
        tiers.isEmpty() -> "$code: no tiers"
        else -> tiers.firstNotNullOfOrNull { it.problem(effects.size) }?.let { "$code: $it" }
    }
}

/**
 * Документы описаний для набора записей.
 */
fun List<ModifierRecord>.toDefinitions(): List<ModifierDefinition> = map { it.toDefinition() }
