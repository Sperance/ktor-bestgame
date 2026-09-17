package ru.descend.domain.modifiers

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.shared.model.StockEntity

/**
 * Описание modifier.
 *
 * Это НЕ modifier конкретного предмета.
 *
 * Это шаблон, из которого создаются конкретные
 * rolled modifiers.
 *
 * Definition можно хранить:
 *
 * - MongoDB
 * - JSON
 * - YAML
 * - memory registry
 *
 * без изменения Kotlin-кода.
 */
@Serializable
@kotlinx.serialization.SerialName("features.logic.modifiers.ModifierDefinition")
data class ModifierDefinition(

    /**
     * Уникальный ID.
     *
     * Например:
     *
     * life
     * fire_resistance
     * increased_physical_damage
     */
    val id: String,

    /**
     * Отображаемое имя.
     */
    val name: String,

    /**
     * Источник modifier.
     */
    val source: ModifierSource,

    /**
     * Область действия.
     */
    val scope: ModifierScope = ModifierScope.ITEM,

    /**
     * Тип affix.
     *
     * PREFIX / SUFFIX / null.
     */
    val affixType: AffixType? = null,

    /**
     * Tier-ы.
     */
    val tiers: List<ModifierTier> = emptyList(),

    /**
     * Теги.
     */
    val tags: Set<ModifierTag> = emptySet(),

    /**
     * Условия.
     */
    val conditions: List<ModifierCondition> = emptyList(),

    /**
     * Эффекты.
     *
     * Один modifier может изменять
     * сразу несколько характеристик.
     */
    val effects: List<ModifierEffect> = emptyList(),

    /**
     * Приоритет применения.
     */
    val priority: Int = 0,

    /**
     * Можно ли получить modifier случайной генерацией.
     */
    val rollable: Boolean = true,

    /**
     * Можно ли иметь несколько одинаковых modifier.
     */
    val stackable: Boolean = false,

    /** Null marks legacy data; published custom definitions opt in to the active catalog. */
    val catalogProfile: String? = null,
    val revision: Int = 1,
    val enabled: Boolean = true,
    val runtimeSupported: Boolean = true,
    val unsupportedStats: List<String> = emptyList(),
    val poe: kotlinx.serialization.json.JsonObject? = null,

    override var _id: String = ObjectId().toHexString()
) : StockEntity
