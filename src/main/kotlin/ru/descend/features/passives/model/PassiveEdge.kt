package ru.descend.features.passives.model

import kotlinx.serialization.Serializable

@Serializable data class PassiveEdge(val from: String, val to: String)
