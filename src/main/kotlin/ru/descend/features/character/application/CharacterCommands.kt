package ru.descend.features.character.application

import kotlinx.serialization.Serializable
import ru.descend.features.character.model.EquipmentSlot

@Serializable data class EquipCommand(val expectedVersion: Long, val equipmentUuid: String, val slot: EquipmentSlot)
@Serializable data class UnequipCommand(val expectedVersion: Long, val slot: EquipmentSlot)
@Serializable data class GrantEquipmentCommand(val expectedVersion: Long, val equipmentId: String)
/**
 * Изменение количества предметов. `amount` — это сколько единиц выдать (>0) или списать (<0),
 * а не размер стака: у персонажа каждая единица хранится отдельным документом.
 */
@Serializable data class ItemDelta(val itemId: String, val amount: Long)
@Serializable data class AdjustItemsCommand(val expectedVersion: Long, val items: List<ItemDelta>)
@Serializable data class RedeemCommand(val expectedVersion: Long, val code: String)
@Serializable data class UseRecipeCommand(val expectedVersion: Long, val recipeId: String, val recipeVersion: Long, val ingredientIds: List<String>, val amount: Long = 1)
