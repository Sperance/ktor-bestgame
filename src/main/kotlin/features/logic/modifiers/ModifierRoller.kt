package features.logic.modifiers

import application.enums.EnumModifierSource
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import features.data.equipment.equipment_data.Equipment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Ролл модификаторов при получении предмета - как в POE:
 * при создании экземпляра предмета из шаблона [Equipment] сразу
 * выбираются случайные префиксы/суффиксы и внутри каждого - случайный тир.
 *
 * Описания модификаторов и тиры берутся из кэшей Mongo-коллекций
 * `ModifierDefinition` и `ModifierTier`.
 */
object ModifierRoller : KoinComponent {

    private val definitionCache: ModifierDefinitionCache by inject()
    private val tierCache: ModifierTierCache by inject()

    /**
     * Модификаторы, которые не роллятся случайно, а всегда есть на предмете.
     */
    private val alwaysApplied = setOf(
        EnumModifierSource.IMPLICIT,
        EnumModifierSource.ENCHANTMENT,
        EnumModifierSource.CORRUPTION,
        EnumModifierSource.UNIQUE
    )

    /**
     * Роллит полный набор модификаторов для нового экземпляра предмета.
     *
     * Пул предмета разбирается по [EnumModifierSource]: количество префиксов
     * и суффиксов задаёт редкость, остальные источники попадают на предмет всегда.
     */
    fun roll(equipment: Equipment): MutableList<Modifier> {
        val pool = definitionCache.findAllById(equipment.modifierIds)

        val prefixes = pool.filter { it.source == EnumModifierSource.PREFIX }
            .shuffled()
            .take(equipment.rarity.prefixCount)
        val suffixes = pool.filter { it.source == EnumModifierSource.SUFFIX }
            .shuffled()
            .take(equipment.rarity.suffixCount)
        val permanent = pool.filter { it.source in alwaysApplied }

        return (permanent + prefixes + suffixes)
            .mapNotNull { roll(it, equipment.itemLevel) }
            .toMutableList()
    }

    /**
     * Роллит один модификатор: выбирает доступный тир и значение внутри его диапазона.
     *
     * @return null, если для описания модификатора не заведено ни одного тира
     */
    fun roll(definition: ModifierDefinition, itemLevel: Int): Modifier? {
        val tier = tierCache.rollTier(definition._id, itemLevel) ?: return null
        return Modifier(
            modifierId = definition._id,
            tierId = tier._id,
            tier = tier.tier,
            values = tier.roll()
        )
    }
}
