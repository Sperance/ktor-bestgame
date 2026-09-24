package features.logic.modifiers

import application.enums.EnumInfluence
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import extensions.randomExt
import extensions.weightedRandomExt
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
     * Роллит набор модификаторов для нового экземпляра предмета.
     *
     * База предмета сюда **не** попадает: она принадлежит шаблону и живёт
     * в справочнике в единственном экземпляре. Копия её не хранит, а расчёт
     * характеристик берёт её оттуда, см. CharacterStatsCalculator. Так одна
     * правка базового типа доезжает до всех уже выданных копий, и ни одно
     * число не лежит в базе данных дважды.
     *
     * Пул разбирается по [EnumModifierSource]: количество префиксов и
     * суффиксов задаёт редкость, остальные источники попадают на предмет всегда.
     *
     * @param rarity редкость, под которую роллим - у экземпляра она своя и её меняют сферы
     */
    fun roll(equipment: Equipment, rarity: EnumRarity = equipment.rarity): MutableList<Modifier> =
        (rollPermanent(equipment) + rollAffixes(equipment, rarity)).toMutableList()

    /**
     * Постоянные модификаторы предмета: implicit, энчанты, порча, модификаторы уникалок.
     * Сферы, которые перекатывают аффиксы, их не трогают.
     */
    fun rollPermanent(equipment: Equipment): List<Modifier> =
        definitionCache.findAllById(equipment.modifierIds)
            .filter { it.source in alwaysApplied }
            .mapNotNull { roll(it, equipment.itemLevel) }

    /**
     * Случайные префиксы и суффиксы на места, которые оставила редкость.
     *
     * Выбор взвешенный ([ModifierDefinition.spawnWeight]) и без повторов группы
     * ([ModifierDefinition.family]): два модификатора одной группы на предмет не встают.
     *
     * @param influence влияние копии - добавляет в пул модификаторы своего влияния
     * @param kept аффиксы, которые остаются на предмете (закреплённые): они занимают
     * свои места и свои группы, и новые роллятся вокруг них
     */
    fun rollAffixes(
        equipment: Equipment,
        rarity: EnumRarity,
        influence: EnumInfluence? = null,
        kept: Collection<Modifier> = emptyList()
    ): List<Modifier> {
        val keptDefinitions = definitions(kept)
        val (prefixes, suffixes) = freeSlots(rarity, keptDefinitions)
        return pickAffixes(affixPool(equipment, influence), prefixes, suffixes, keptDefinitions.map { it.family() })
            .mapNotNull { roll(it, equipment.itemLevel) }
    }

    /**
     * Роллит один аффикс сверх уже имеющихся - то, что делают сферы улучшения и Exalted Orb.
     *
     * Свободным считается место того вида (префикс или суффикс), которого
     * на предмете меньше, чем позволяет редкость.
     *
     * @return null, если свободного места нет или пул исчерпан
     */
    fun rollExtraAffix(
        equipment: Equipment,
        rarity: EnumRarity,
        current: Collection<Modifier>,
        influence: EnumInfluence? = null
    ): Modifier? = rollOne(affixPool(equipment, influence), equipment.itemLevel, rarity, current)

    /**
     * Роллит один модификатор из пула влияния - то, что делает сфера влияния.
     *
     * @return null, если свободного места нет или все группы пула уже заняты
     */
    fun rollInfluenced(equipment: Equipment, rarity: EnumRarity, current: Collection<Modifier>, influence: EnumInfluence): Modifier? =
        rollOne(influencePool(influence), equipment.itemLevel, rarity, current)

    private fun rollOne(pool: List<ModifierDefinition>, itemLevel: Int, rarity: EnumRarity, current: Collection<Modifier>): Modifier? {
        val currentDefinitions = definitions(current)
        val (prefixes, suffixes) = freeSlots(rarity, currentDefinitions)
        return pickAffixes(pool, minOf(prefixes, 1), minOf(suffixes, 1), currentDefinitions.map { it.family() })
            .firstNotNullOfOrNull { roll(it, itemLevel) }
    }

    /**
     * Взвешенный выбор аффиксов на свободные места, без повторов группы.
     *
     * Чистая функция над описаниями: ни кэшей, ни тиров - поэтому правила выбора
     * проверяются тестом без базы. Места двух видов заполняются вперемешку, как
     * тянутся из одного мешка, пока не кончатся места или подходящие описания.
     *
     * @param taken группы, которые на предмете уже есть
     */
    fun pickAffixes(
        pool: Collection<ModifierDefinition>,
        prefixes: Int,
        suffixes: Int,
        taken: Collection<String> = emptyList()
    ): List<ModifierDefinition> {
        val families = taken.toMutableSet()
        var freePrefixes = prefixes
        var freeSuffixes = suffixes
        val picked = mutableListOf<ModifierDefinition>()

        while (true) {
            val next = pool.filter { candidate ->
                candidate.family() !in families && when (candidate.source) {
                    EnumModifierSource.PREFIX -> freePrefixes > 0
                    EnumModifierSource.SUFFIX -> freeSuffixes > 0
                    else -> false
                }
            }.weightedRandomExt { it.spawnWeight } ?: break

            picked += next
            families += next.family()
            if (next.source == EnumModifierSource.PREFIX) freePrefixes-- else freeSuffixes--
        }
        return picked
    }

    /**
     * Сколько префиксов и суффиксов ещё помещается на предмет.
     */
    fun freeSlots(rarity: EnumRarity, current: Collection<ModifierDefinition>): Pair<Int, Int> =
        (rarity.prefixCount - current.count { it.source == EnumModifierSource.PREFIX }).coerceAtLeast(0) to
            (rarity.suffixCount - current.count { it.source == EnumModifierSource.SUFFIX }).coerceAtLeast(0)

    /**
     * Перекатывает значения модификаторов, сохраняя сами модификаторы и их тиры -
     * то, что делает Divine Orb.
     *
     * Закреплённые аффиксы не перекатываются никогда.
     *
     * @param filter какие модификаторы перекатывать, по умолчанию все
     */
    fun rerollValues(
        modifiers: Collection<Modifier>,
        filter: (ModifierDefinition) -> Boolean = { true }
    ): MutableList<Modifier> = modifiers.mapTo(mutableListOf()) { modifier ->
        // Закреплённый аффикс неизменен целиком, значения тоже
        if (modifier.fractured) return@mapTo modifier
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
    /** Тот же модификатор на тир выше (с 0.38.0); лучший тир остаётся собой, значения перебрасываются. */
    fun raiseTier(modifier: Modifier): Modifier? {
        if (modifier.fractured || modifier.tier <= 1) return null
        val tier = tierCache.findByModifier(modifier.modifierId).firstOrNull { it.tier == modifier.tier - 1 } ?: return null
        return modifier.copy(tierId = tier._id, tier = tier.tier, values = tier.roll())
    }

    /** Модификатор по коду описания на уровне предмета. */
    fun rollCode(code: String, itemLevel: Int): Modifier? = definitionCache.findByCode(code)?.let { roll(it, itemLevel) }

    fun definitions(modifiers: Collection<Modifier>): List<ModifierDefinition> =
        modifiers.mapNotNull { definitionCache.findById(it.modifierId) }

    /**
     * Аффикс ли это - то есть трогают ли его сферы.
     */
    fun isAffix(modifier: Modifier): Boolean = definitionCache.findById(modifier.modifierId)?.isAffix() == true

    /**
     * Поставлен ли модификатор верстаком.
     */
    fun isCrafted(modifier: Modifier): Boolean = definitionCache.findById(modifier.modifierId)?.crafted == true

    /**
     * Есть ли на предмете место под ещё один аффикс.
     */
    fun hasFreeAffixSlot(rarity: EnumRarity, current: Collection<Modifier>): Boolean =
        freeSlots(rarity, definitions(current)).let { (prefixes, suffixes) -> prefixes > 0 || suffixes > 0 }

    /**
     * Пул аффиксов предмета: обычные из шаблона и, если копия под влиянием, модификаторы этого влияния.
     * Ремесленных здесь нет никогда - их ставит только верстак.
     */
    private fun affixPool(equipment: Equipment, influence: EnumInfluence?): List<ModifierDefinition> =
        definitionCache.findAllById(equipment.modifierIds).filter { it.isNaturalAffix() } +
            (influence?.let(::influencePool) ?: emptyList())

    /**
     * Модификаторы одного влияния. Они общие для всех шаблонов: влияние открывает свой
     * пул любому предмету, на который его можно наложить.
     */
    private fun influencePool(influence: EnumInfluence): List<ModifierDefinition> =
        definitionCache.getCache().filter { it.influence == influence && it.isAffix() && !it.crafted }
}
