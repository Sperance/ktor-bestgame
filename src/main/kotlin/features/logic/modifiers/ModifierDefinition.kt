package features.logic.modifiers

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

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

    val poe: kotlinx.serialization.json.JsonObject? = null,

    override var _id: String = ObjectId().toHexString()
) : StockEntity