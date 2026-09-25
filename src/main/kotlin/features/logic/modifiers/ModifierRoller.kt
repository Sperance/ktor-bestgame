package features.logic.modifiers

import application.enums.EnumInfluence
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import extensions.weightedRandomExt
import features.caches.ModifierDefinitionCache
import features.data.equipment.equipment_data.Equipment
import features.logic.pools.Pools
import features.logic.pools.Weighted
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
 * Описания модификаторов с их тирами берутся из кэша коллекции `ModifierDefinition`,
 * состав пулов - из кэша `Pool`.
 */
object ModifierRoller : KoinComponent {

    private val definitionCache: ModifierDefinitionCache by inject()

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
     * Префиксы и суффиксы тянутся из пулов шаблона в количестве, которое задаёт редкость;
     * закреплённые модификаторы шаблона попадают на предмет всегда.
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
        definitionCache.findAllByCode(equipment.fixedModifierCodes)
            .filter { it.source in alwaysApplied }
            .mapNotNull { roll(it, equipment.itemLevel) }

    /**
     * Случайные префиксы и суффиксы на места, которые оставила редкость.
     *
     * Выбор взвешенный (вес модификатора в пулах шаблона) и без повторов группы
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
        // Сколько аффиксов - велит редкость (0.53.0: таблица карт стала общей для всех предметов).
        val limit = (rarity.affixes.random() - kept.size).coerceAtLeast(0)
        return pickAffixes(affixPool(equipment, influence), prefixes, suffixes, keptDefinitions.map { it.family() }, limit)
            .mapNotNull { roll(it, equipment.itemLevel) }
    }

    /**
     * Доводит аффиксы копии до правил её редкости (0.53.0): лишние сверх мест префиксов и суффиксов
     * снимаются, а недостающие до минимума дороллены. Закреплённые аффиксы не трогаются.
     *
     * @return новые модификаторы или null, если копия уже в порядке
     */
    fun normalize(equipment: Equipment, rarity: EnumRarity, params: List<Modifier>, influence: EnumInfluence? = null): MutableList<Modifier>? {
        if (rarity.fixed) return null
        val result = params.toMutableList()
        listOf(EnumModifierSource.PREFIX to rarity.prefixCount, EnumModifierSource.SUFFIX to rarity.suffixCount).forEach { (source, cap) ->
            val removable = result.filter { !it.fractured && definitionCache.findByCode(it.modifierCode)?.source == source }
            val over = result.count { definitionCache.findByCode(it.modifierCode)?.source == source } - cap
            if (over > 0) result.removeAll(removable.takeLast(over).toSet())
        }
        while (result.count(::isAffix) < rarity.affixes.first)
            result += rollExtraAffix(equipment, rarity, result, influence) ?: break
        return result.takeIf { it != params }
    }

    /**
     * Правило «волшебный или редкий предмет - не пустой» (0.65.0): копия, у которой аффиксов меньше
     * дна её редкости, дороллена до него. Одно место на все пути - ролл, сферы, ремесло, торговец,
     * старые копии в базе, - поэтому предмет без модификаторов не появляется ни откуда.
     *
     * @return true, если копия изменилась
     */
    fun ensureAffixes(equipment: Equipment, item: features.data.inventory.CharacterEquipment): Boolean {
        val rarity = item.rarity
        if (rarity.fixed || rarity.affixes.first == 0 || item.params.count(::isAffix) >= rarity.affixes.first) return false
        val fixed = normalize(equipment, rarity, item.params, item.influence) ?: return false
        item.params = fixed
        return true
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
    ): Modifier? {
        if (current.count(::isAffix) >= rarity.affixes.last) return null
        return rollOne(affixPool(equipment, influence), equipment.itemLevel, rarity, current)
    }

    /**
     * Роллит один модификатор из пула влияния - то, что делает сфера влияния.
     *
     * @return null, если свободного места нет или все группы пула уже заняты
     */
    fun rollInfluenced(equipment: Equipment, rarity: EnumRarity, current: Collection<Modifier>, influence: EnumInfluence): Modifier? =
        rollOne(pool(listOf(Pools.influence(influence))), equipment.itemLevel, rarity, current)

    private fun rollOne(pool: List<Weighted<ModifierDefinition>>, itemLevel: Int, rarity: EnumRarity, current: Collection<Modifier>): Modifier? {
        val currentDefinitions = definitions(current)
        val (prefixes, suffixes) = freeSlots(rarity, currentDefinitions)
        return pickAffixes(pool, minOf(prefixes, 1), minOf(suffixes, 1), currentDefinitions.map { it.family() }, limit = 1)
            .firstOrNull()?.let { roll(it, itemLevel) }
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
        pool: Collection<Weighted<ModifierDefinition>>,
        prefixes: Int,
        suffixes: Int,
        taken: Collection<String> = emptyList(),
        limit: Int = Int.MAX_VALUE,
    ): List<ModifierDefinition> {
        val families = taken.toHashSet()
        var freePrefixes = prefixes
        var freeSuffixes = suffixes
        // Кандидаты отсеиваются по мере выбора: занятая группа и исчерпанный вид уходят из мешка,
        // и каждая тяга - один проход по тому, что осталось, а не по всему пулу заново.
        val candidates = pool.filterTo(ArrayList()) { (candidate) ->
            candidate.family() !in families &&
                (candidate.source == EnumModifierSource.PREFIX || candidate.source == EnumModifierSource.SUFFIX)
        }
        val picked = mutableListOf<ModifierDefinition>()
        while (picked.size < limit && candidates.isNotEmpty()) {
            val next = candidates.weightedRandomExt { (candidate, weight) ->
                if (if (candidate.source == EnumModifierSource.PREFIX) freePrefixes > 0 else freeSuffixes > 0) weight else 0
            }?.value ?: break
            picked += next
            val family = next.family()
            if (next.source == EnumModifierSource.PREFIX) freePrefixes-- else freeSuffixes--
            candidates.removeAll { (candidate) ->
                candidate.family() == family ||
                    (candidate.source == EnumModifierSource.PREFIX && freePrefixes == 0) ||
                    (candidate.source == EnumModifierSource.SUFFIX && freeSuffixes == 0)
            }
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
        val definition = definitionCache.findByCode(modifier.modifierCode)
        if (definition == null || !filter(definition)) return@mapTo modifier

        val tier = definition.tier(modifier.tier) ?: return@mapTo modifier
        modifier.copy(values = tier.roll())
    }

    /**
     * Один модификатор из пулов [tags] по их весам - порча Vaal Orb, ручная работа ремесла.
     *
     * @return null, если в пулах никого нет
     */
    fun rollFrom(tags: List<String>, itemLevel: Int): Modifier? = Pools.draw(pool(tags))?.let { roll(it, itemLevel) }

    /** Модификаторы пулов [tags] с их весами, см. [features.logic.pools.PoolTable.of]. */
    fun pool(tags: List<String>): List<Weighted<ModifierDefinition>> = definitionCache.pool(tags)

    /**
     * Роллит один модификатор: выбирает доступный тир и значение внутри его диапазона.
     *
     * @return null, если для описания модификатора не заведено ни одного тира
     */
    fun roll(definition: ModifierDefinition, itemLevel: Int): Modifier? {
        val (number, tier) = definitionCache.rollTier(definition.code, itemLevel) ?: return null
        return Modifier(modifierCode = definition.code, values = tier.roll(), tier = number)
    }

    /** Тот же модификатор на тир выше (с 0.38.0); лучший тир остаётся собой, значения перебрасываются. */
    fun raiseTier(modifier: Modifier): Modifier? {
        if (modifier.fractured || modifier.tier <= 1) return null
        val tier = definitionCache.findByCode(modifier.modifierCode)?.tier(modifier.tier - 1) ?: return null
        return modifier.copy(tier = modifier.tier - 1, values = tier.roll())
    }

    /** Модификатор по коду описания на уровне предмета. */
    fun rollCode(code: String, itemLevel: Int): Modifier? = definitionCache.findByCode(code)?.let { roll(it, itemLevel) }

    /**
     * Описания переданных модификаторов.
     */
    fun definitions(modifiers: Collection<Modifier>): List<ModifierDefinition> =
        modifiers.mapNotNull { definitionCache.findByCode(it.modifierCode) }

    /**
     * Аффикс ли это - то есть трогают ли его сферы.
     */
    fun isAffix(modifier: Modifier): Boolean = definitionCache.findByCode(modifier.modifierCode)?.isAffix() == true

    /**
     * Поставлен ли модификатор верстаком.
     */
    fun isCrafted(modifier: Modifier): Boolean = definitionCache.findByCode(modifier.modifierCode)?.crafted == true

    /**
     * Пул аффиксов предмета: пулы шаблона и, если копия под влиянием, пул этого влияния - он общий
     * для всех шаблонов. Ремесленных здесь нет никогда - их ставит только верстак.
     */
    fun affixPool(equipment: Equipment, influence: EnumInfluence? = null): List<Weighted<ModifierDefinition>> =
        definitionCache.affixPool(if (influence == null) equipment.modifierPools else equipment.modifierPools + Pools.influence(influence))
}
