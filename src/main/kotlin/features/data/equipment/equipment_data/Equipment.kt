package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.entity.StockEntity
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

interface EquipmentInterface {
    var slot: EnumEquipmentType
    var name: String
    var rarity: EnumRarity
    var itemLevel: Int
    var description: String
    var image: String?

    /**
     * Пул модификаторов предмета - ссылки на [ModifierDefinition._id].
     *
     * Что с ними произойдёт при создании экземпляра, решает [EnumModifierSource]
     * самого описания: PREFIX и SUFFIX роллятся случайно и в количестве,
     * которое задаёт редкость, остальные (IMPLICIT, ENCHANTMENT, CORRUPTION,
     * UNIQUE) попадают на каждый экземпляр предмета.
     *
     */
    var modifierIds: MutableList<String>

    /**
     * База предмета - броня, урон, скорость атаки - готовыми модификаторами
     * с фиксированными значениями.
     *
     * Отдельных полей под базу нет: расчёт характеристик знает ровно один
     * способ получить значение. В отличие от [modifierIds] здесь ничего
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
