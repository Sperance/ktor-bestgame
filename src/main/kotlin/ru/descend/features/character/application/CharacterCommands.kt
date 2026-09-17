package ru.descend.features.character.application

import kotlinx.serialization.Serializable
import ru.descend.features.character.model.EquipmentSlot

@Serializable data class EquipCommand(val expectedVersion: Long, val equipmentUuid: String, val slot: EquipmentSlot)
@Serializable data class UnequipCommand(val expectedVersion: Long, val slot: EquipmentSlot)
@Serializable data class GrantEquipmentCommand(val expectedVersion: Long, val equipmentId: String)
@Serializable data class AdjustItemsCommand(val expectedVersion: Long, val items: List<ru.descend.features.character.model.CharacterItems>)
@Serializable data class RedeemCommand(val expectedVersion: Long, val code: String)
@Serializable data class UseRecipeCommand(val expectedVersion: Long, val recipeId: String, val recipeVersion: Long, val ingredientIds: List<String>, val amount: Long = 1)
