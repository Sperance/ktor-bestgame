package ru.descend.features.passives.application

import kotlinx.serialization.encodeToString
import ru.descend.features.character.model.Character
import ru.descend.features.character.application.EquipmentService
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.combat.domain.BattleStatus
import ru.descend.features.passives.domain.PassiveRules
import ru.descend.features.passives.model.*
import ru.descend.features.passives.persistence.*
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.infrastructure.http.AppJson
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.http.*

class PassiveService(private val characters: CharacterRepository, private val equipment: EquipmentService,
    private val trees: PassiveTreeRepository, private val receipts: PassiveReceiptRepository) {
    suspend fun tree(revision: Int) = trees.definition(revision)
    private fun own(c: Character, actor: Actor) { if (c.deleted || c.userId != actor.id) missing() }
    suspend fun state(id: String, actor: Actor): PassiveState {
        val c = characters.findById(checkedId(id)) ?: missing(); own(c, actor)
        return state(c)
    }
    private suspend fun state(c: Character): PassiveState {
        val rules = PassiveRules(tree(c.passiveTreeRevision))
        rules.validate(c.passiveNodes, c.level.toInt())
        val locked = if (c.battle?.status == BattleStatus.ACTIVE) "Завершите текущий бой" else null
        val spent = rules.cost(c.passiveNodes); val total = rules.budget(c.level.toInt())
        return PassiveState(c.version, c.passiveTreeRevision, c.passiveNodes, total, spent, total - spent,
            if (locked == null) rules.allocatable(c.passiveNodes, c.level.toInt()) else emptySet(),
            if (locked == null) rules.refundable(c.passiveNodes) else emptySet(), equipment.view(c).stats, locked)
    }
    suspend fun change(id: String, actor: Actor, command: PassiveCommand): PassiveState {
        checkedId(id)
        if (!command.requestId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) invalid("Invalid requestId")
        val payload = AppJson.encodeToString(command)
        return transactionExecute("passives.command", retryTransientErrors = true) { session ->
            val old = characters.findById(id, session) ?: missing(); own(old, actor)
            val key = PoeCatalog.stableId("passives:${actor.id}:$id:${command.requestId}")
            receipts.findById(key, session)?.let {
                if (it.payload != payload) invalid("requestId was used for another passive command")
                return@transactionExecute it.result
            }
            checkVersion(old.version, command.expectedVersion)
            if (old.passiveTreeRevision != command.treeRevision) conflict()
            if (old.battle?.status == BattleStatus.ACTIVE) invalid("Завершите бой перед изменением навыков")
            val rules = PassiveRules(tree(old.passiveTreeRevision))
            val next = old.copy(passiveNodes = rules.transition(old.passiveNodes, old.level.toInt(), command.action, command.nodeId))
            // Refunding attributes must not leave equipped items with unsatisfied requirements.
            equipment.validate(next)
            characters.update(next, session)
            val result = state(next)
            receipts.insert(PassiveReceipt(key, payload, result), session)
            result
        }
    }
}
