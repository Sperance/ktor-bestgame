package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Одно действие модификатора: какой стат он меняет и каким способом.
 *
 * Составной (гибридный) модификатор, как в POE, состоит из нескольких эффектов:
 * "+# to maximum Life and Mana" - это два эффекта, ADD по здоровью и ADD по мане.
 */
@Serializable
data class ModifierEffect(

    /**
     * Стат, который изменяется.
     */
    val stat: IntEnumStat,

    /**
     * Способ применения значения.
     */
    val operation: EnumModifierOperation,
)

/**
 * Описание возможного модификатора. Отдельная коллекция Mongo `ModifierDefinition`.
 *
 * Это НЕ модификатор конкретного предмета: здесь нет ни значений, ни тира -
 * только то, ЧТО модификатор делает. Диапазоны значений вынесены в
 * коллекцию [ModifierTier], по одному документу на тир.
 *
 * Например:
 *
 * "Life and Mana" (PREFIX) - два эффекта, ADD по здоровью и ADD по мане,
 * плюс тиры 1..8 с диапазонами значений для каждого эффекта.
 */
@Serializable
data class ModifierDefinition(

    /**
     * Стабильный код модификатора. Уникален в пределах коллекции,
     * используется сидером и внешними инструментами вместо _id.
     */
    val code: String,

    /**
     * Что модификатор делает. Один эффект - обычный модификатор,
     * несколько - составной.
     */
    val effects: List<ModifierEffect>,

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
) : StockEntity {

    /**
     * Составной модификатор меняет больше одного стата за раз.
     */
    fun isComposite(): Boolean = effects.size > 1

    /**
     * Все статы, которых касается модификатор.
     */
    fun stats(): List<IntEnumStat> = effects.map { it.stat }
}
