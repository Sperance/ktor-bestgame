package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Источник модификатора.
 *
 * Не определяет сам эффект.
 * Определяет только откуда этот модификатор появился.
 */
@Serializable
enum class ModifierSource {

    /**
     * Базовый implicit предмета.
     */
    BASE_ITEM,

    /**
     * Префикс.
     */
    PREFIX,

    /**
     * Суффикс.
     */
    SUFFIX,

    /**
     * Уникальный модификатор.
     */
    UNIQUE,

    /**
     * Enchantment.
     */
    ENCHANTMENT,

    /**
     * Corruption.
     */
    CORRUPTION,

    /**
     * Пассивное дерево.
     */
    PASSIVE,

    /**
     * Навык.
     */
    SKILL,

    /**
     * Aura.
     */
    AURA,

    /**
     * Flask / временный эффект.
     */
    FLASK,

    /**
     * Jewel.
     */
    JEWEL,

    /**
     * Map modifier.
     */
    MAP,

    /**
     * Monster modifier.
     */
    MONSTER,

    /**
     * Временный игровой эффект.
     */
    TEMPORARY,

    /**
     * Системный модификатор.
     */
    SYSTEM
}