package features.modifier

import application.enums.EnumModifierType
import application.enums.EnumStatHelper
import application.enums.EnumStatType
import application.model.Stat
import application.model.StatModifier
import features.equipment.Equipment

/**
 * Расчёт итоговых характеристик персонажа с учётом всех модификаторов (PoE-формула).
 *
 * ## Формула (PoE-стиль):
 * ```
 * final = (base + Σ FLAT_ADD)
 *       × (1 + (Σ INCREASED − Σ REDUCED) / 100)
 *       × Π (1 + MORE_i / 100)
 *       × Π (1 − LESS_i / 100)
 * ```
 *
 * PENETRATION и LEECH не влияют на значение характеристики напрямую —
 * они хранятся как отдельные бонусы и применяются в боёвой логике.
 *
 * ## Пример:
 * ```
 * Базовое здоровье: 100
 * +50 FLAT_ADD      → base becomes 150
 * 20% INCREASED     → 150 × 1.20 = 180
 * 30% MORE          → 180 × 1.30 = 234
 * ```
 */
object StatCalculationService {

    /**
     * Результат расчёта характеристик персонажа.
     *
     * @param statId    ID характеристики (FK → property table)
     * @param statEnum  Enum характеристики (null если неизвестен)
     * @param base      Базовое значение из [Stat] персонажа
     * @param flat      Сумма всех FLAT_ADD модификаторов
     * @param increased Сумма всех INCREASED
     * @param reduced   Сумма всех REDUCED
     * @param moreList  Список MORE значений (перемножаются независимо)
     * @param lessList  Список LESS значений (делятся независимо)
     * @param final     Итоговое значение
     */
    data class StatResult(
        val statId: Long,
        val statEnum: EnumStatHelper?,
        val base: Double,
        val flat: Double,
        val increased: Double,
        val reduced: Double,
        val moreList: List<Double>,
        val lessList: List<Double>,
        val final: Double
    )

    /**
     * Рассчитать итоговое значение одной характеристики.
     *
     * @param baseValue  Базовое значение (из Character.params)
     * @param modifiers  Все модификаторы этой характеристики (с предметов)
     * @return Итоговое значение
     */
    fun calculateStat(baseValue: Double, modifiers: List<StatModifier>): Double {
        val flat     = modifiers.filter { it.modType == EnumModifierType.FLAT_ADD }.sumOf { it.value }
        val increased = modifiers.filter { it.modType == EnumModifierType.INCREASED }.sumOf { it.value }
        val reduced  = modifiers.filter { it.modType == EnumModifierType.REDUCED }.sumOf { it.value }
        val moreList = modifiers.filter { it.modType == EnumModifierType.MORE }.map { it.value }
        val lessList = modifiers.filter { it.modType == EnumModifierType.LESS }.map { it.value }

        var result = baseValue + flat
        result *= (1.0 + (increased - reduced) / 100.0)
        moreList.forEach { result *= (1.0 + it / 100.0) }
        lessList.forEach { result *= (1.0 - it / 100.0) }

        return result.coerceAtLeast(0.0)
    }

    /**
     * Рассчитать все характеристики персонажа с учётом надетых предметов.
     *
     * @param characterParams   Базовые характеристики из Character.params (MutableSet<Stat>)
     * @param equippedItems     Список надетых предметов (equippedSlot != null)
     * @param statEnumById      Маппинг statId → EnumStatHelper из PropertyCache
     * @return Список [StatResult] по каждой задействованной характеристике
     */
    fun calculateCharacterStats(
        characterParams: Set<Stat>,
        equippedItems: List<Equipment>,
        statEnumById: Map<Long, EnumStatHelper>
    ): List<StatResult> {

        // Собрать все моды с надетых предметов
        val allMods = equippedItems.flatMap { item ->
            val implicit = item.implicit ?: emptySet()
            val explicit = item.modifiers ?: emptySet()
            implicit + explicit
        }
        val modsByStatId = allMods.groupBy { it.statId }

        // Базовые статы персонажа
        val baseByStatId = characterParams
            .filter { it.type == EnumStatType.STOCK }
            .associateBy({ it.key }, { it.value })

        // Объединить все statId
        val allStatIds = (baseByStatId.keys + modsByStatId.keys).toSet()

        return allStatIds.map { statId ->
            val base   = baseByStatId[statId] ?: 0.0
            val mods   = modsByStatId[statId] ?: emptyList()
            val enum   = statEnumById[statId]

            val flat     = mods.filter { it.modType == EnumModifierType.FLAT_ADD }.sumOf { it.value }
            val increased = mods.filter { it.modType == EnumModifierType.INCREASED }.sumOf { it.value }
            val reduced  = mods.filter { it.modType == EnumModifierType.REDUCED }.sumOf { it.value }
            val moreList = mods.filter { it.modType == EnumModifierType.MORE }.map { it.value }
            val lessList = mods.filter { it.modType == EnumModifierType.LESS }.map { it.value }

            var final = base + flat
            final *= (1.0 + (increased - reduced) / 100.0)
            moreList.forEach { final *= (1.0 + it / 100.0) }
            lessList.forEach { final *= (1.0 - it / 100.0) }
            final = final.coerceAtLeast(0.0)

            StatResult(
                statId = statId,
                statEnum = enum,
                base = base,
                flat = flat,
                increased = increased,
                reduced = reduced,
                moreList = moreList,
                lessList = lessList,
                final = final
            )
        }.sortedBy { it.statId }
    }

    /**
     * Краткий формат: statId → итоговое значение.
     * Удобен для быстрого доступа к конкретной характеристике.
     */
    fun calculateCharacterStatMap(
        characterParams: Set<Stat>,
        equippedItems: List<Equipment>,
        statEnumById: Map<Long, EnumStatHelper>
    ): Map<Long, Double> =
        calculateCharacterStats(characterParams, equippedItems, statEnumById)
            .associate { it.statId to it.final }
}
