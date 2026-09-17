package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.InventoryResponse")
data class InventoryResponse(val version: Long, val equipment: List<ru.descend.features.character.model.CharacterEquipments>)
