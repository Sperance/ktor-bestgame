package ru.descend.features.poe.persistence

import kotlinx.serialization.Serializable
import ru.descend.features.poe.domain.PoeResult
import ru.descend.shared.model.StockEntity

@Serializable
@kotlinx.serialization.SerialName("features.poe.PoeReceipt")
data class PoeReceipt(override var _id: String, val payload: String, val result: PoeResult) : StockEntity
