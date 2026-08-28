package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumStatHelper
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Конкретный модификатор конкретного предмета.
 *
 * ModifierDefinition:
 *     "Strength Tier 3 = 25..39"
 *
 * Modifier:
 *     "Strength = 35"
 */
@Serializable
data class Modifier(

    /**
     * Стат.
     *
     * Дублируем из definition специально,
     * чтобы предмет можно было считать без lookup definition.
     */
    val stat: EnumStatHelper,

    /**
     * Операция.
     */
    val operation: EnumModifierOperation,

    /**
     * Реально выпавшее значение.
     */
    val value: Double,

    /**
     * Источник модификатора.
     */
    val source: EnumModifierSource,

    /**
     * Tier.
     */
    val tier: Int = 1,

    /**
     * Уникальный ID самого roll.
     */
    var _id: String = ObjectId().toHexString()
)