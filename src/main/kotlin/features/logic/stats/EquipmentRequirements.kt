package features.logic.stats

import application.enums.EnumStatStock
import application.enums.IntEnumStat
import features.data.equipment.equipment_data.Equipment

/**
 * Не выполненное требование предмета.
 */
data class UnmetRequirement(
    val name: String,
    val required: Int,
    val actual: Int,
)

/**
 * Требования к надеванию экипировки.
 *
 * Как в POE: атрибуты считаются с учётом уже активной экипировки, поэтому
 * предмет, чьи требования перестали выполняться, не слетает - он остаётся
 * в слоте, но перестаёт работать.
 */
object EquipmentRequirements {

    /**
     * Что из требований предмета не выполнено. Пустой список - предмет работает.
     *
     * @param level уровень персонажа
     * @param stats характеристики, посчитанные на текущий момент
     */
    fun unmet(template: Equipment, level: Int, stats: Map<IntEnumStat, Double>): List<UnmetRequirement> {
        val result = mutableListOf<UnmetRequirement>()

        check("level", template.requiredLevel, level)?.let { result.add(it) }
        check("strength", template.requiredStrength, stat(stats, EnumStatStock.STOCK_STRENGTH))?.let { result.add(it) }
        check("dexterity", template.requiredDexterity, stat(stats, EnumStatStock.STOCK_AGILITY))?.let { result.add(it) }
        check("intelligence", template.requiredIntelligence, stat(stats, EnumStatStock.STOCK_INTELLECT))?.let { result.add(it) }

        return result
    }

    /**
     * Выполнены ли все требования предмета.
     */
    fun isMet(template: Equipment, level: Int, stats: Map<IntEnumStat, Double>): Boolean =
        unmet(template, level, stats).isEmpty()

    private fun check(name: String, required: Int, actual: Int): UnmetRequirement? =
        if (required <= actual) null else UnmetRequirement(name, required, actual)

    private fun stat(stats: Map<IntEnumStat, Double>, stat: IntEnumStat): Int =
        (stats[stat] ?: 0.0).toInt()

    /** Требует ли шаблон хоть чего-то: без требований лист перед ним пересчитывать незачем. */
    fun demanding(template: Equipment): Boolean =
        template.requiredLevel > 1 || template.requiredStrength > 0 || template.requiredDexterity > 0 || template.requiredIntelligence > 0
}
