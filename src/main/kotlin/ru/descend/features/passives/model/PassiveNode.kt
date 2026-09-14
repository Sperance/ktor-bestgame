package ru.descend.features.passives.model

import kotlinx.serialization.Serializable

@Serializable enum class PassiveNodeKind { ORIGIN, SMALL, NOTABLE, KEYSTONE }
@Serializable data class PassiveNode(val id: String, val name: String, val description: String, val kind: PassiveNodeKind,
    val x: Double, val y: Double, val effects: List<PassiveEffect>, val cost: Int = 1)
