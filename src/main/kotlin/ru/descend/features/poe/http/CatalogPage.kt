package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import ru.descend.features.poe.catalog.PoeRecord

@Serializable
@kotlinx.serialization.SerialName("features.poe.CatalogPage")
data class CatalogPage(val items: List<PoeRecord>, val page: Int, val size: Int, val total: Int)
