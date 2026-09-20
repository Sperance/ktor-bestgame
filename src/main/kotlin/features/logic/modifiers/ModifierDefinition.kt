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
 *
 * Если задан [perStat], эффект становится конверсией: значение умножается на
 * то, сколько раз [perAmount] укладывается в уже посчитанный стат-источник.
 * Так выражаются и атрибутные конверсии ("+1 к здоровью за каждые 2 Силы"),
 * и узлы дерева вида "1% increased Evasion per 5 Dexterity".
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

    /**
     * Стат-источник конверсии. null - обычный эффект с фиксированным значением.
     *
     * Порядок источника обязан быть строго меньше порядка [stat]: только это
     * и делает циклы конверсий невыразимыми. Проверяется на сиде.
     */
    val perStat: IntEnumStat? = null,

    /**
     * Сколько единиц источника дают одно значение эффекта.
     * Имеет смысл только вместе с [perStat].
     */
    val perAmount: Double = 1.0,
) {
    /**
     * Конверсия ли это - то есть зависит ли значение от другого стата.
     */
    fun isConversion(): Boolean = perStat != null
}

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
     * Локальный модификатор считается внутри своего предмета и наружу отдаёт
     * уже свёрнутый результат - как "#% increased Armour" на нагруднике в POE,
     * который умножает броню только этого нагрудника.
     *
     * Глобальный действует на персонажа целиком - как тот же процент на кольце.
     * Поэтому локальная и глобальная версии одного стата это два разных описания.
     */
    val isLocal: Boolean = false,

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
