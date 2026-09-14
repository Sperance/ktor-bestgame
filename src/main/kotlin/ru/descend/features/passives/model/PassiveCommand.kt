package ru.descend.features.passives.model

import kotlinx.serialization.Serializable

@Serializable enum class PassiveAction { ALLOCATE, REFUND, RESET }
@Serializable data class PassiveCommand(val expectedVersion: Long, val treeRevision: Int, val requestId: String,
    val action: PassiveAction, val nodeId: String? = null)
