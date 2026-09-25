package features.logic.modifiers

import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import base.entity.StockEntity
import features.logic.pools.Pooled
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
     * Дополнительные теги.
     */
    val tags: MutableList<String>? = null,

    /**
     * Группа модификатора, как в POE: на одном предмете не бывает двух модификаторов
     * одной группы. Так ремесленный "+# к здоровью" не встаёт рядом с выпавшим, а
     * два тира одного свойства не складываются. null - группа совпадает с кодом.
     */
    val group: String? = null,

    /**
     * В каких пулах модификатор состоит и с каким весом (с 0.39.0): `helmet`, `local:armor`,
     * `influence:SHAPER`, `corruption`, `handcrafted:smith`. Пул называет источник - шаблон,
     * сфера, ремесло, - а вес решает, как часто модификатор выпадает среди соседей.
     */
    override val pools: Map<String, Int> = emptyMap(),

    /**
     * Влияние, без которого модификатор не выпадает. null - обычный модификатор.
     *
     * Пул таких модификаторов - `influence:<влияние>`: его открывает предмету
     * его собственное влияние, см. ModifierRoller.
     */
    val influence: EnumInfluence? = null,

    /**
     * Ремесленный модификатор: его не роллит ни одна сфера, его ставит верстак.
     * Занимает префикс или суффикс, как выпавший, и на предмете такой один.
     */
    val crafted: Boolean = false,

    override var _id: String = ObjectId().toHexString()
) : StockEntity, Pooled {

    /**
     * Группа, по которой модификаторы исключают друг друга на одном предмете.
     */
    fun family(): String = group ?: code

    /**
     * Занимает ли модификатор место префикса или суффикса.
     */
    fun isAffix(): Boolean = source == EnumModifierSource.PREFIX || source == EnumModifierSource.SUFFIX

    /**
     * Роллится ли модификатор сферами из пула шаблона: аффикс без влияния и не с верстака.
     */
    fun isNaturalAffix(): Boolean = isAffix() && influence == null && !crafted

    /**
     * Составной модификатор меняет больше одного стата за раз.
     */
    fun isComposite(): Boolean = effects.size > 1

    /**
     * Выведен ли модификатор из игры (с 0.43.0): он касается маны или заклинаний, которых больше
     * нет, и каждый его пул весит ноль - поэтому не роллится нигде, а уже выпавшие копии остаются.
     */
    fun isRetired(): Boolean =
        pools.isNotEmpty() && pools.values.all { it == 0 } && effects.any { (it.stat as? Enum<*>)?.name in RETIRED_STATS }

    /**
     * Все статы, которых касается модификатор.
     */
    fun stats(): List<IntEnumStat> = effects.map { it.stat }
}

/**
 * Характеристики, убранные из игры: мана и заклинания (0.43.0). Их
 * модификаторы больше не роллятся, а уже выпавшие копии остаются на вещах.
 */
val RETIRED_STATS = setOf(
    "STOCK_MANA", "STOCK_SPELL_BLOCK", "STOCK_ATTACK_MAGICAL", "STOCK_CAST_SPEED", "STOCK_MANA_REGEN",
    "STOCK_LEECH_MAGICAL", "STOCK_MANA_ON_KILL", "STOCK_MANA_ON_HIT", "STOCK_CAST_STRENGTH",
)
