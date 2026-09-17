package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Страница инвентаря: [next] передаётся в параметр `after` следующего запроса,
 * `null` означает конец списка. Инвентарь не ограничен по длине, поэтому он всегда отдаётся частями.
 */
@Serializable
@kotlinx.serialization.SerialName("features.poe.InventoryResponse")
data class InventoryResponse(val version: Long, val equipment: List<ru.descend.features.character.model.CharacterEquipments>,
    val total: Long, val size: Int, val next: String? = null)
