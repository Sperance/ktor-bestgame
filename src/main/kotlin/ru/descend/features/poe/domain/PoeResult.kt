package ru.descend.features.poe.domain

import ru.descend.features.poe.catalog.PoeCatalog

import ru.descend.features.poe.catalog.string

import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.CharacterItems
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
@kotlinx.serialization.SerialName("features.poe.PoeResult")
data class PoeResult(val requestId: String, val characterVersion: Long, val equipment: CharacterEquipments, val currencyRemaining: Long? = null)
