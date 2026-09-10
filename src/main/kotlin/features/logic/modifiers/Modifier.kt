package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Конкретный rolled modifier.
 *
 * ModifierDefinition = шаблон.
 *
 * Modifier = конкретный экземпляр этого шаблона.
 *
 * Например:
 *
 * Definition:
 * +50..100 Life
 *
 * Modifier:
 * +73 Life
 */
@Serializable
data class Modifier(

    /**
     * ID definition.
     *
     * Не enum.
     */
    val definitionId: String,

    /**
     * Rolled values.
     *
     * Может быть несколько.
     */
    val values: List<ModifierValue>,

    /**
     * Tier.
     */
    val tier: Int,

    /**
     * Источник.
     *
     * Сохраняем в экземпляре для быстрого доступа.
     */
    val source: ModifierSource,

    /**
     * Дополнительные runtime tags.
     */
    val tags: Set<ModifierTag> = emptySet(),
    val definitionRevision: Int = 1
) {

    /**
     * Получить первое значение.
     */
    val value: Double
        get() = values.firstOrNull()?.value ?: 0.0

    /**
     * Получить значение по индексу.
     */
    fun value(index: Int): Double {
        return values.getOrNull(index)?.value ?: 0.0
    }
}