package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.entity.StockEntity
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.pools.Pooled
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

interface EquipmentInterface : Pooled {
    var slot: EnumEquipmentType

    /**
     * Стабильный код шаблона. Им предмет ссылается на свой текст
     * в файлах локализации: equipment.<code>.name и .description.
     *
     * Названия и описания в документе не хранятся - см. locale/ru.json.
     */
    var code: String

    var rarity: EnumRarity
    var itemLevel: Int

    /**
     * Закреплённые модификаторы шаблона - ссылки на [ModifierDefinition._id]: implicit базы и строки
     * уникалки. Каждый попадает на каждый экземпляр предмета ([EnumModifierSource] у них не
     * PREFIX и не SUFFIX), сферы, перекатывающие аффиксы, их не трогают.
     */
    var fixedModifierIds: MutableList<String>

    /**
     * Пулы, из которых предмет роллит префиксы и суффиксы (с 0.39.0) - теги, а не список кодов:
     * пул слота (`helmet`) и локальные пулы базы (`local:armor`). Кто в них состоит и с каким весом,
     * говорит сам модификатор, см. [ModifierDefinition.pools]. Порядок - приоритет: вес модификатора
     * берётся из первого пула, в котором он состоит.
     */
    var modifierPools: MutableList<String>

    /**
     * В каких пулах экипировки состоит сам шаблон и с каким весом: `drop`, `smith`, `merchant`,
     * `unique:world`, `unique:chance`, `unique:smith`, `boss:<код>`.
     */
    override var pools: Map<String, Int>

    /**
     * База предмета - броня, урон, скорость атаки - готовыми модификаторами
     * с фиксированными значениями.
     *
     * Отдельных полей под базу нет: расчёт характеристик знает ровно один
     * способ получить значение. В отличие от [modifierPools] здесь ничего
     * не роллится - база базового типа в POE тоже не случайна.
     */
    var baseParams: MutableList<Modifier>

    /**
     * Уровень, с которого предмет можно надеть.
     */
    var requiredLevel: Int

    /**
     * Сила, необходимая чтобы надеть предмет.
     */
    var requiredStrength: Int

    /**
     * Ловкость, необходимая чтобы надеть предмет.
     */
    var requiredDexterity: Int

    /**
     * Интеллект, необходимый чтобы надеть предмет.
     */
    var requiredIntelligence: Int
}

/**
 * Шаблон предмета экипировки (коллекция `Equipment`).
 *
 * Шаблон не хранит зароленных значений: конкретный экземпляр предмета
 * с его модификаторами лежит отдельным документом в коллекции инвентаря.
 */
@Serializable
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),

    var price: Long = 1L,

) : StockEntity, EquipmentInterface {
    open fun calculatePrice(): Long {
        var result = 0L
        result += (itemLevel * 50)
        // Пул общий для класса предметов, ценность даёт число слотов под модификаторы
        result += ((rarity.prefixCount + rarity.suffixCount) * 150)
        result += ((rarity.ordinal + 1) * 300)
        return result
    }
}
