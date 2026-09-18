package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Описание возможного модификатора. Отдельная коллекция Mongo `ModifierDefinition`.
 *
 * Это НЕ модификатор конкретного предмета: здесь нет ни значения, ни тира —
 * только то, ЧТО модификатор делает. Диапазоны значений вынесены в
 * коллекцию [ModifierTier], по одному документу на тир.
 *
 * Например:
 *
 * "Strength" (PREFIX, ADD) + тиры 1..8 с диапазонами значений.
 */
@Serializable
data class ModifierDefinition(

    /**
     * Стабильный код модификатора. Уникален в пределах коллекции,
     * используется сидером и внешними инструментами вместо _id.
     */
    val code: String,

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
     * Отображаемое имя.
     */
    val name: String? = null,

    /**
     * Дополнительные теги.
     */
    val tags: MutableList<String>? = null,

    override var _id: String = ObjectId().toHexString()
) : StockEntity
