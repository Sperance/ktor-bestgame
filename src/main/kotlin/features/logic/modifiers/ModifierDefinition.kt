package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
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
     * Отображаемое имя.
     */
    val name: String? = null,

    /**
     * Стат, который изменяется.
     */
    val stat: IntEnumStat,

    /**
     * Способ применения значения.
     */
    val operation: EnumModifierOperation,

    /**
     * Откуда модификатор появился.
     */
    val source: EnumModifierSource,

    /**
     * MAX Tier модификатора.
     */
    val tierMax: Int = 8,

    /**
     * Минимальный требуемый item level.
     */
    val minItemLevel: Int = 1,

    val stepValue: Double = 1.0,

    val constValue: Double? = null,

    /**
     * Дополнительные теги.
     */
    val tags: MutableList<String>? = null,

    var _id: String = ObjectId().toHexString()
)