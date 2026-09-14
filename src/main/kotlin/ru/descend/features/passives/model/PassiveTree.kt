package ru.descend.features.passives.model

import kotlinx.serialization.Serializable

@Serializable data class PassiveTree(val revision: Int, val name: String, val rootId: String, val nodes: List<PassiveNode>, val edges: List<PassiveEdge>)
