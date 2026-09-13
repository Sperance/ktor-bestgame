package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.PublishModifierRequest")
data class PublishModifierRequest(val definition: ru.descend.domain.modifiers.ModifierDefinition, val expectedRevision: Int)
