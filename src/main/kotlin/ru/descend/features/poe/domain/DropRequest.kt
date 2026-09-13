package ru.descend.features.poe.domain

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.DropRequest")
data class DropRequest(val requestId: String, val expectedVersion: Long)
