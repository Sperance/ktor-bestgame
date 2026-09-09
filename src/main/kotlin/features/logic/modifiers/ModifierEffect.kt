package features.logic.modifiers

import features.logic.stats.StatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Семантический эффект modifier.
 *
 * Важно:
 *
 * ModifierOperation отвечает на вопрос:
 * "как применить?"
 *
 * ModifierEffect отвечает:
 * "что именно происходит?"
 */
@Serializable
sealed interface ModifierEffect {

    /**
     * Изменение обычного stat.
     *
     * Например:
     *
     * +100 Life
     * 20% increased Armor
     * 30% more Damage
     */
    @Serializable
    @SerialName("stat")
    data class Stat(
        val stat: StatId,
        val operation: ModifierOperation,
        val value: ValueExpression
    ) : ModifierEffect

    /**
     * Изменение другого stat относительно первого.
     *
     * Например:
     *
     * Strength grants X% increased melee damage.
     */
    @Serializable
    @SerialName("derived_stat")
    data class DerivedStat(
        val targetStat: StatId,
        val sourceStat: StatId,
        val operation: ModifierOperation,
        val value: ValueExpression
    ) : ModifierEffect

    /**
     * Конвертация одного типа урона в другой.
     */
    @Serializable
    @SerialName("damage_conversion")
    data class DamageConversion(
        val from: String,
        val to: String,
        val percentage: ValueExpression
    ) : ModifierEffect

    /**
     * Damage taken as.
     */
    @Serializable
    @SerialName("damage_taken_as")
    data class DamageTakenAs(
        val from: String,
        val to: String,
        val percentage: ValueExpression
    ) : ModifierEffect

    /**
     * Penetration.
     */
    @Serializable
    @SerialName("penetration")
    data class Penetration(
        val damageType: String,
        val percentage: ValueExpression
    ) : ModifierEffect

    /**
     * Получение ресурса.
     */
    @Serializable
    @SerialName("gain_resource")
    data class GainResource(
        val resource: String,
        val amount: ValueExpression
    ) : ModifierEffect

    /**
     * Шанс применить status effect.
     */
    @Serializable
    @SerialName("chance_to_apply")
    data class ChanceToApply(
        val effectId: String,
        val chance: ValueExpression
    ) : ModifierEffect

    /**
     * Добавить tag.
     */
    @Serializable
    @SerialName("add_tag")
    data class AddTag(
        val tag: String
    ) : ModifierEffect

    /**
     * Удалить tag.
     */
    @Serializable
    @SerialName("remove_tag")
    data class RemoveTag(
        val tag: String
    ) : ModifierEffect

    /**
     * Создание другого эффекта.
     */
    @Serializable
    @SerialName("grant_effect")
    data class GrantEffect(
        val effectId: String
    ) : ModifierEffect
}