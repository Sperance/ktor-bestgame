package ru.descend.features.poe.domain

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.CraftRequest")
data class CraftRequest(val requestId: String, val equipmentUuid: String, val currency: PoeCurrency, val expectedVersion: Long)
