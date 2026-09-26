package config

import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumModifierVariant
import application.enums.EnumMonsterRarity
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
 * Вариант семейства в файле (0.66.0): какой и с какими тирами. Без своих тиров вариант
 * наследует тиры семейства - так локальная копия аффикса ничем, кроме места расчёта, не отличается.
 */
@Serializable
data class VariantRecord(
    val variant: EnumModifierVariant,
    val tiers: List<ModifierTier> = emptyList(),
    /** Считается ли вариант внутри предмета: верстачный «% брони» локален, как выпавший. */
    val local: Boolean = false,
)

/**
 * Семейство модификаторов в том виде, в каком оно лежит в `content/modifiers.json` (с 0.39.0).
 *
 * Тир 1 - первая строка таблицы, у каждой свой item level, свои диапазоны и свой вес, ровно как
 * в POE; с 0.56.0 тиры так и лежат внутри описания. Несколько эффектов = составной (гибридный)
 * модификатор. С 0.66.0 запись - семейство: [variants] рождают описания-копии с теми же
 * эффектами ([EnumModifierVariant]), а модификатор монстра несёт [minRarity]. В каких пулах
 * состоит каждое описание, говорит `content/pools.json`, см. [PoolSeeder].
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
    val variants: List<VariantRecord> = emptyList(),
    val minRarity: EnumMonsterRarity? = null,
) {

    /** Природное описание семейства. */
    fun toDefinition(): ModifierDefinition = definition(EnumModifierVariant.NATURAL, tiers)

    /** Природное описание и все варианты семейства. */
    fun toDefinitions(): List<ModifierDefinition> =
        listOf(toDefinition()) + variants.map { definition(it.variant, it.tiers.ifEmpty { tiers }, it.local) }

    private fun definition(variant: EnumModifierVariant, tiers: List<ModifierTier>, variantLocal: Boolean = false): ModifierDefinition {
        val code = EnumModifierVariant.code(code, variant)
        return ModifierDefinition(
            code = code,
            effects = effects.map { ModifierEffect(it.stat, it.operation, it.perStat, it.perAmount) },
            source = when (variant) {
                EnumModifierVariant.IMPLICIT -> EnumModifierSource.IMPLICIT
                EnumModifierVariant.CORRUPTED -> EnumModifierSource.CORRUPTION
                EnumModifierVariant.ENCHANT -> EnumModifierSource.ENCHANTMENT
                else -> source
            },
            tiers = tiers,
            isLocal = local || variantLocal || variant == EnumModifierVariant.LOCAL,
            tags = (tags + listOfNotNull(variant.suffix?.lowercase())).toMutableList(),
            group = group,
            influence = influence,
            crafted = crafted || variant == EnumModifierVariant.CRAFTED,
            family = this.code,
            variant = variant,
            minRarity = minRarity,
            _id = code.toStableObjectId()
        )
    }

    /** Ошибка файла, названная по коду, или null. */
    fun problem(): String? = when {
        code.contains(EnumModifierVariant.SEPARATOR) -> "$code: a family code carries no variant separator"
        tiers.isEmpty() -> "$code: no tiers"
        source == EnumModifierSource.MONSTER && minRarity == null -> "$code: a monster modifier needs minRarity"
        source != EnumModifierSource.MONSTER && minRarity != null -> "$code: minRarity on a non-monster modifier"
        variants.map { it.variant }.let { it.toSet().size != it.size || EnumModifierVariant.NATURAL in it } -> "$code: variants"
        variants.any { v -> (v.variant == EnumModifierVariant.LOCAL || v.variant == EnumModifierVariant.CRAFTED) && source != EnumModifierSource.PREFIX && source != EnumModifierSource.SUFFIX } ->
            "$code: a local or crafted variant of a non-affix"
        else -> (tiers + variants.flatMap { it.tiers }).firstNotNullOfOrNull { it.problem(effects.size) }?.let { "$code: $it" }
    }
}

/**
 * Документы описаний для набора записей: семейства и их варианты.
 */
fun List<ModifierRecord>.toDefinitions(): List<ModifierDefinition> = flatMap { it.toDefinitions() }
