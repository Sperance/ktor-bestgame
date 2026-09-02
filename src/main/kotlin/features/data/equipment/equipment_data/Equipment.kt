package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumModifierDefinitions
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.entity.StockEntity
import extensions.RandomExt
import extensions.printLog
import extensions.to1Digits
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import kotlin.math.min

interface EquipmentInterface {
    var slot: EnumEquipmentType
    var name: String
    var rarity: EnumRarity
    var itemLevel: Int
    var description: String
    var image: String?
    var modifiers: ArrayList<Modifier>?
    var modifierDefinitions: List<EnumModifierDefinitions>?
    var modifierDefinitionsStock: List<EnumModifierDefinitions>?
}

@Serializable
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),

    var price: Long = 1L,

) : StockEntity, EquipmentInterface {
    open fun calculatePrice(): Long {
        var result = 0L
        result += (itemLevel * 50)
        result += ((modifiers?.size ?: (0 * 150)))
        result += ((rarity.ordinal + 1) * 300)
        return result
    }

    fun rollModifiers(forceNew: Boolean = false): ArrayList<Modifier> {
        if (forceNew || modifiers == null) {
            val rolledMods = ArrayList<Modifier>()

            // Определяем количество префиксов и суффиксов по редкости
            val (prefixCount, suffixCount) = getModifierCountsByRarity()

            val availablePrefixes = modifierDefinitions?.filter { it.definition.source == EnumModifierSource.PREFIX } ?: emptyList()
            val availableSuffixes = modifierDefinitions?.filter { it.definition.source == EnumModifierSource.SUFFIX } ?: emptyList()

            // Выбираем случайные префиксы
            val selectedPrefixes = availablePrefixes.shuffled().take(prefixCount)
            // Выбираем случайные суффиксы
            val selectedSuffixes = availableSuffixes.shuffled().take(suffixCount)

            // Роллим значения для префиксов
            for (def in selectedPrefixes) {
                rolledMods.add(rollModifierFromDefinition(def))
            }

            // Роллим значения для суффиксов
            for (def in selectedSuffixes) {
                rolledMods.add(rollModifierFromDefinition(def))
            }

            modifierDefinitionsStock?.forEach { md ->
                rolledMods.add(rollModifierFromDefinition(md))
            }

            modifiers = rolledMods
        }

        return modifiers!!
    }

    /**
     * Возвращает количество префиксов и суффиксов в зависимости от редкости
     */
    private fun getModifierCountsByRarity(): Pair<Int, Int> {
        return when (rarity) {
            EnumRarity.COMMON -> Pair(1, 1)
            EnumRarity.UNCOMMON -> Pair(1, 1)
            EnumRarity.RARE -> Pair(1, 1)
            EnumRarity.EPIC -> Pair(2, 1)
            EnumRarity.LEGENDARY -> Pair(2, 2)
            EnumRarity.MYTHICAL -> Pair(3, 3)
        }
    }

    /**
     * Создаёт конкретный Modifier из ModifierDefinition со случайным значением в диапазоне
     */
    private fun rollModifierFromDefinition(definition: EnumModifierDefinitions): Modifier {
        val maxTier = itemLevel / 10
        val tierRolled = RandomExt.randomInt(1..min(definition.definition.tierMax, maxTier))
        val rolledValue = (definition.definition.constValue ?: ((definition.definition.stepValue + tierRolled) * tierRolled)).to1Digits()
        return Modifier(
            type = definition,
            value = rolledValue,
            tier = tierRolled
        )
    }
}