package features.logic.modifiers

import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.IntEnumStat
import base.entity.StockEntity
import extensions.RandomExt
import extensions.to1Digits
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
}

/**
 * Тир модификатора (с 0.56.0 - внутри описания, отдельной коллекции `ModifierTier` больше нет).
 *
 * Нумерация как в POE: тир 1 - первый в списке [ModifierDefinition.tiers], лучший, он даёт
 * максимальные значения и требует самый высокий item level; дальше тиры слабеют.
 *
 * @property level минимальный item level предмета, на котором тир может выпасть
 * @property values диапазоны `[min, max]`, по одному на каждый эффект описания и в том же порядке
 */
@Serializable
data class ModifierTier(val level: Int = 1, val values: List<List<Double>>) {

    /**
     * Роллит значения тира - по одному на каждый эффект описания.
     *
     * Качество ролла общее для всех эффектов: составной модификатор
     * не может выпасть максимумом по здоровью и минимумом по мане.
     */
    fun roll(): List<Double> {
        val progress = RandomExt.randomProgress()
        return values.map { (min, max) -> (min + (max - min) * progress).to1Digits() }
    }

    /** Ошибка тира или null: по диапазону `[min, max]` на каждый из [effects] эффектов. */
    fun problem(effects: Int): String? = when {
        level < 1 -> "level $level"
        values.size != effects -> "$effects effects, ${values.size} values"
        values.any { it.size != 2 || it[0] > it[1] } -> "a [min, max] per effect"
        else -> null
    }
}

/**
 * Описание возможного модификатора. Коллекция Mongo `ModifierDefinition`.
 *
 * Это НЕ модификатор конкретного предмета: здесь нет выпавших значений - только то, ЧТО
 * модификатор делает, и его тиры с диапазонами. Экземпляры предметов, верстак, дерево и пулы
 * ссылаются на описание по стабильному [code] (с 0.56.0), а не по `_id`.
 *
 * Например "Life and Mana" (PREFIX) - два эффекта, ADD по здоровью и ADD по мане,
 * плюс тиры 1..8 с диапазонами значений для каждого эффекта.
 */
@Serializable
data class ModifierDefinition(

    /**
     * Стабильный код модификатора. Уникален в пределах коллекции; им на описание ссылается всё.
     */
    override val code: String,

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
     * Тиры от лучшего (тир 1) к худшему. Пусто у пассивок, которым значения задаёт узел дерева.
     */
    val tiers: List<ModifierTier> = emptyList(),

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

    /** Тир по номеру (1 - лучший). */
    fun tier(number: Int): ModifierTier? = tiers.getOrNull(number - 1)

    /**
     * Все статы, которых касается модификатор.
     */
    fun stats(): List<IntEnumStat> = effects.map { it.stat }
}
