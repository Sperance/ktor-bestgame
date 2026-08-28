package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumStatHelper
import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Описание возможного модификатора.
 *
 * Это НЕ модификатор конкретного предмета.
 *
 * Например:
 *
 * "Strength Tier 3"
 * 25..39 Strength
 *
 * Именно ModifierDefinition используется
 * при генерации предмета.
 */
@Serializable
data class ModifierDefinition(

    /**
     * Уникальный код модификатора.
     *
     * Например:
     * strength_t3
     */
    val code: String,

    /**
     * Отображаемое имя.
     */
    val name: String,

    /**
     * Стат, который изменяется.
     */
    val stat: EnumStatHelper,

    /**
     * Способ применения значения.
     */
    val operation: EnumModifierOperation,

    /**
     * Откуда модификатор появился.
     */
    val source: EnumModifierSource,

    /**
     * Tier модификатора.
     */
    val tier: Int = 1,

    /**
     * Минимальный item level.
     */
    val minItemLevel: Int = 1,

    /**
     * Минимальное значение roll.
     */
    val minValue: Double = 0.0,

    /**
     * Максимальное значение roll.
     */
    val maxValue: Double = 0.0,

    /**
     * Вес при случайной генерации.
     */
    val weight: Int = 100,

    /**
     * Дополнительные теги.
     *
     * Например:
     * attack
     * physical
     * melee
     */
    val tags: Set<String> = emptySet(),

    var _id: String = ObjectId().toHexString()
)