package ru.descend.features.passives.persistence

import kotlinx.serialization.Serializable
import ru.descend.features.passives.model.PassiveState
import ru.descend.shared.model.StockEntity

@Serializable data class PassiveReceipt(override var _id: String, val payload: String, val result: PassiveState) : StockEntity
