package features.modifier

import application.enums.EnumEquipmentType
import application.enums.EnumModifierCategory
import application.enums.EnumModifierGroup
import application.enums.EnumModifierType
import application.enums.EnumStatHelper
import application.enums.EnumStatModifierHelper

/**
 * Определение одного тира аффикса.
 *
 * @param tier          Номер тира: 1 (лучший) .. 8 (худший)
 * @param minItemLevel  Минимальный уровень предмета для доступа к тиру
 * @param minValue      Минимальное значение диапазона
 * @param maxValue      Максимальное значение диапазона
 * @param weight        Вес тира при случайном выборе (меньший номер тира = ниже вес)
 */
data class ModifierTierDef(
    val tier: Int,
    val minItemLevel: Int,
    val minValue: Double,
    val maxValue: Double,
    val weight: Int
)

/**
 * Шаблон модификатора предмета — описывает какой аффикс может появиться на предмете.
 *
 * Используется [ModifierPool] и [ItemGenerationService] для генерации предметов.
 * НЕ хранится в БД — только в памяти.
 *
 * @param source          Enum-источник (для идентификации)
 * @param name            Отображаемое название аффикса
 * @param stat            Какую характеристику меняет
 * @param modType         Как складывается (FLAT_ADD, INCREASED, MORE...)
 * @param category        PREFIX, SUFFIX или IMPLICIT
 * @param group           Группа — на предмете может быть только один мод из группы
 * @param tiers           Список тиров от 1 до 8 (sorted ascending by tier number)
 * @param allowedSlots    В каких слотах может появиться этот мод
 * @param tags            Теги для фильтрации
 * @param weight          Базовый вес при выборе (может быть перезаписан тиром)
 */
data class ModifierTemplate(
    val source: EnumStatModifierHelper,
    val name: String,
    val stat: EnumStatHelper,
    val modType: EnumModifierType,
    val category: EnumModifierCategory,
    val group: EnumModifierGroup,
    val tiers: List<ModifierTierDef>,
    val allowedSlots: Set<EnumEquipmentType>,
    val tags: List<String>,
    val weight: Int
) {
    /**
     * Найти наилучший доступный тир для данного уровня предмета.
     * Возвращает null если ни один тир недоступен (itemLevel слишком мал).
     */
    fun bestTierFor(itemLevel: Int): ModifierTierDef? =
        tiers.filter { it.minItemLevel <= itemLevel }.minByOrNull { it.tier }

    /**
     * Получить взвешенный список тиров, доступных при данном уровне предмета.
     * Тиры с меньшим номером (лучшие) имеют меньший вес, поэтому они редки.
     */
    fun availableTiers(itemLevel: Int): List<ModifierTierDef> =
        tiers.filter { it.minItemLevel <= itemLevel }
}
