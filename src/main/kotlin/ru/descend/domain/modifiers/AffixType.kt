package ru.descend.domain.modifiers

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.logic.modifiers.AffixType")
enum class AffixType {

    PREFIX,

    SUFFIX
}
