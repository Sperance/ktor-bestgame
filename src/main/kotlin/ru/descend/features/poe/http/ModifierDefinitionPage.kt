package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.ModifierDefinitionPage")
data class ModifierDefinitionPage(val items: List<ru.descend.domain.modifiers.ModifierDefinition>, val page: Int, val size: Int, val total: Int,
    /** definitionId -> идентификатор иконки. Картинка: /api/v1/icons/{icon}.svg */
    val icons: Map<String, String> = emptyMap())
