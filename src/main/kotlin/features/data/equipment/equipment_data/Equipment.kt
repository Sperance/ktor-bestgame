package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import features.logic.modifiers.AffixType
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierGenerator
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

interface EquipmentInterface {
    var slot: EnumEquipmentType
    var name: String
    var rarity: EnumRarity
    var itemLevel: Int
    var description: String
    var image: String?
    var modifiers: ArrayList<Modifier>?

    /**
     * Definition-ы, доступные для генерации
     * конкретного предмета.
     *
     * Это уже не enum.
     */
    var modifierDefinitions: List<ModifierDefinition>?

    /**
     * Всегда присутствующие modifier definitions.
     */
    var modifierDefinitionsStock: List<ModifierDefinition>?
}

@Serializable
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),
    var price: Long = 1L,
    var poeBaseId: String? = null
) : StockEntity, EquipmentInterface {

    open fun calculatePrice(): Long {
        var result = 0L
        result += itemLevel * 50L
        result += (modifiers?.size ?: 0) * 150L
        result += (rarity.ordinal + 1) * 300L
        return result
    }

    /**
     * Генерация modifier.
     *
     * Вся логика генерации находится
     * во внешнем ModifierGenerator.
     */
    fun rollModifiers(generator: ModifierGenerator, forceNew: Boolean = false): ArrayList<Modifier> {

        if (!forceNew && modifiers != null) {
            return modifiers!!
        }

        val result = ArrayList<Modifier>()
        val (prefixCount, suffixCount) = getModifierCountsByRarity()
        val definitions = modifierDefinitions.orEmpty()

        val prefixes = definitions.filter { it.affixType == AffixType.PREFIX }
        val suffixes = definitions.filter { it.affixType == AffixType.SUFFIX }

        result += generator.generate(
            definitions = prefixes,
            itemLevel = itemLevel,
            count = prefixCount
        )

        result += generator.generate(
            definitions = suffixes,
            itemLevel = itemLevel,
            count = suffixCount
        )

        result += generator.generate(
            definitions = modifierDefinitionsStock.orEmpty(),
            itemLevel = itemLevel,
            count = modifierDefinitionsStock?.size ?: 0
        )

        modifiers = result
        price = calculatePrice()
        return result
    }

    private fun getModifierCountsByRarity(): Pair<Int, Int> {
        return when (rarity) {
            EnumRarity.COMMON -> 1 to 1
            EnumRarity.UNCOMMON -> 1 to 1
            EnumRarity.RARE -> 1 to 1
            EnumRarity.EPIC -> 2 to 1
            EnumRarity.LEGENDARY -> 2 to 2
            EnumRarity.MYTHICAL -> 3 to 3
        }
    }
}