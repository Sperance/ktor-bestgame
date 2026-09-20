package features.logic.modifiers

import application.enums.EnumModifierSource
import application.enums.EnumRarity
import extensions.randomExt
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
 * Здесь же операции, которыми пользуются сферы: доролл одного аффикса,
 * перекат значений, ролл порчи.
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
     * База предмета переносится как есть, пул разбирается по [EnumModifierSource]:
     * количество префиксов и суффиксов задаёт редкость, остальные источники
     * попадают на предмет всегда.
     *
     * @param rarity редкость, под которую роллим - у экземпляра она своя и её меняют сферы
     */
    fun roll(equipment: Equipment, rarity: EnumRarity = equipment.rarity): MutableList<Modifier> =
        (equipment.baseParams + rollPermanent(equipment) + rollAffixes(equipment, rarity)).toMutableList()

    /**
     * Постоянные модификаторы предмета: implicit, энчанты, порча, модификаторы уникалок.
     * Сферы, которые перекатывают аффиксы, их не трогают.
     */
    fun rollPermanent(equipment: Equipment): List<Modifier> =
        definitionCache.findAllById(equipment.modifierIds)
            .filter { it.source in alwaysApplied }
            .mapNotNull { roll(it, equipment.itemLevel) }

    /**
     * Случайные префиксы и суффиксы в количестве, которое задаёт редкость.
     *
     * @param exclude id описаний, которые выбирать нельзя (уже есть на предмете)
     */
    fun rollAffixes(
        equipment: Equipment,
        rarity: EnumRarity,
        exclude: Collection<String> = emptyList()
    ): List<Modifier> {
        val pool = affixPool(equipment, exclude)

        val prefixes = pool.filter { it.source == EnumModifierSource.PREFIX }
            .shuffled()
            .take(rarity.prefixCount)
        val suffixes = pool.filter { it.source == EnumModifierSource.SUFFIX }
            .shuffled()
            .take(rarity.suffixCount)

        return (prefixes + suffixes).mapNotNull { roll(it, equipment.itemLevel) }
    }

    /**
     * Роллит один аффикс сверх уже имеющихся - то, что делают сферы улучшения и Exalted Orb.
     *
     * Свободным считается место того вида (префикс или суффикс), которого
     * на предмете меньше, чем позволяет редкость.
     *
     * @return null, если свободного места нет или пул исчерпан
     */
    fun rollExtraAffix(equipment: Equipment, rarity: EnumRarity, current: Collection<Modifier>): Modifier? {
        val currentDefinitions = definitions(current)
        val prefixes = currentDefinitions.count { it.source == EnumModifierSource.PREFIX }
        val suffixes = currentDefinitions.count { it.source == EnumModifierSource.SUFFIX }

        val candidates = affixPool(equipment, currentDefinitions.map { it._id }).filter {
            when (it.source) {
                EnumModifierSource.PREFIX -> prefixes < rarity.prefixCount
                EnumModifierSource.SUFFIX -> suffixes < rarity.suffixCount
                else -> false
            }
        }

        return candidates.shuffled().firstNotNullOfOrNull { roll(it, equipment.itemLevel) }
    }

    /**
     * Перекатывает значения модификаторов, сохраняя сами модификаторы и их тиры -
     * то, что делает Divine Orb.
     *
     * @param filter какие модификаторы перекатывать, по умолчанию все
     */
    fun rerollValues(
        modifiers: Collection<Modifier>,
        filter: (ModifierDefinition) -> Boolean = { true }
    ): MutableList<Modifier> = modifiers.mapTo(mutableListOf()) { modifier ->
        val definition = definitionCache.findById(modifier.modifierId)
        if (definition == null || !filter(definition)) return@mapTo modifier

        val tier = tierCache.findById(modifier.tierId) ?: return@mapTo modifier
        modifier.copy(values = tier.roll())
    }

    /**
     * Случайный модификатор порчи - то, что вешает Vaal Orb.
     *
     * @return null, если в справочнике нет ни одного модификатора с источником CORRUPTION
     */
    fun rollCorruption(itemLevel: Int): Modifier? {
        val candidates = definitionCache.getCache().filter { it.source == EnumModifierSource.CORRUPTION }
        if (candidates.isEmpty()) return null
        return roll(candidates.randomExt(), itemLevel)
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

    /**
     * Описания переданных модификаторов.
     */
    fun definitions(modifiers: Collection<Modifier>): List<ModifierDefinition> =
        modifiers.mapNotNull { definitionCache.findById(it.modifierId) }

    /**
     * Аффикс ли это - то есть трогают ли его сферы.
     */
    fun isAffix(modifier: Modifier): Boolean {
        val source = definitionCache.findById(modifier.modifierId)?.source ?: return false
        return source == EnumModifierSource.PREFIX || source == EnumModifierSource.SUFFIX
    }

    /**
     * Есть ли на предмете место под ещё один аффикс.
     */
    fun hasFreeAffixSlot(rarity: EnumRarity, current: Collection<Modifier>): Boolean {
        val currentDefinitions = definitions(current)
        val prefixes = currentDefinitions.count { it.source == EnumModifierSource.PREFIX }
        val suffixes = currentDefinitions.count { it.source == EnumModifierSource.SUFFIX }
        return prefixes < rarity.prefixCount || suffixes < rarity.suffixCount
    }

    /**
     * Пул аффиксов предмета за вычетом уже занятых описаний.
     */
    private fun affixPool(equipment: Equipment, exclude: Collection<String>): List<ModifierDefinition> =
        definitionCache.findAllById(equipment.modifierIds)
            .filter { it.source == EnumModifierSource.PREFIX || it.source == EnumModifierSource.SUFFIX }
            .filterNot { it._id in exclude }
}
