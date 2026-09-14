package ru.descend.features.passives.persistence

import kotlinx.serialization.Serializable
import ru.descend.features.passives.model.PassiveTree
import ru.descend.shared.model.StockEntity

@Serializable data class PassiveTreeDocument(override var _id: String, val tree: PassiveTree) : StockEntity
