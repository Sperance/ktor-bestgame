package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import ru.descend.features.poe.domain.PoeCurrency

@Serializable
@kotlinx.serialization.SerialName("features.poe.CurrencyOption")
data class CurrencyOption(val id: PoeCurrency, val name: String, val itemId: String)
