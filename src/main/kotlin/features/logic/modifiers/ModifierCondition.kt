package features.logic.modifiers

import features.logic.stats.StatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Условие активации modifier.
 */
@Serializable
sealed interface ModifierCondition {

    /**
     * Всегда активно.
     */
    @Serializable
    @SerialName("always")
    data object Always : ModifierCondition

    /**
     * У персонажа есть stat не меньше указанного.
     */
    @Serializable
    @SerialName("stat_at_least")
    data class StatAtLeast(
        val stat: StatId,
        val value: Double
    ) : ModifierCondition

    /**
     * У персонажа есть stat не больше указанного.
     */
    @Serializable
    @SerialName("stat_at_most")
    data class StatAtMost(
        val stat: StatId,
        val value: Double
    ) : ModifierCondition

    /**
     * Персонаж имеет tag.
     */
    @Serializable
    @SerialName("has_tag")
    data class HasTag(
        val tag: String
    ) : ModifierCondition

    /**
     * Цель имеет tag.
     */
    @Serializable
    @SerialName("target_has_tag")
    data class TargetHasTag(
        val tag: String
    ) : ModifierCondition

    /**
     * Персонаж на full life.
     */
    @Serializable
    @SerialName("full_life")
    data object FullLife : ModifierCondition

    /**
     * Персонаж на low life.
     */
    @Serializable
    @SerialName("low_life")
    data object LowLife : ModifierCondition

    /**
     * Есть определённый effect.
     */
    @Serializable
    @SerialName("has_effect")
    data class HasEffect(
        val effectId: String
    ) : ModifierCondition

    /**
     * Логическое AND.
     */
    @Serializable
    @SerialName("and")
    data class And(
        val conditions: List<ModifierCondition>
    ) : ModifierCondition

    /**
     * Логическое OR.
     */
    @Serializable
    @SerialName("or")
    data class Or(
        val conditions: List<ModifierCondition>
    ) : ModifierCondition

    /**
     * Логическое NOT.
     */
    @Serializable
    @SerialName("not")
    data class Not(
        val condition: ModifierCondition
    ) : ModifierCondition
}