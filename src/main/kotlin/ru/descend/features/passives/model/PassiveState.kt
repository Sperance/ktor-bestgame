package ru.descend.features.passives.model

import kotlinx.serialization.Serializable
import ru.descend.features.character.domain.CharacterStats

@Serializable data class PassiveState(val characterVersion: Long, val treeRevision: Int, val allocated: Set<String>,
    val totalPoints: Int, val spentPoints: Int, val availablePoints: Int, val allocatable: Set<String>,
    val refundable: Set<String>, val stats: CharacterStats, val lockedReason: String? = null)
