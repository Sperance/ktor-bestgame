package ru.descend.features.equipment.model

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.modifiers.AffixType
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.ModifierDefinition
import ru.descend.domain.modifiers.ModifierGenerator
import ru.descend.shared.model.VersionedEntity
import kotlinx.datetime.LocalDateTime
import ru.descend.shared.extensions.now

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
@kotlinx.serialization.SerialName("features.data.equipment.equipment_data.Equipment")
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
    var price: Long = 1L,
    var poeBaseId: String? = null,
    var modifierDefinitionRefs: List<ru.descend.domain.modifiers.ModifierRef> = emptyList(),
    var stockModifierDefinitionRefs: List<ru.descend.domain.modifiers.ModifierRef> = emptyList()
) : VersionedEntity, EquipmentInterface {

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
