package ru.descend.features.combat.domain

import kotlinx.serialization.Serializable

@Serializable enum class BattleAction { ATTACK, POWER, GUARD, POTION, FLEE }
@Serializable enum class BattleStatus { ACTIVE, VICTORY, DEFEAT, FLED }
@Serializable data class Combatant(
    val name: String, val maxLife: Double, val life: Double, val maxMana: Double = 0.0,
    val mana: Double = 0.0, val shield: Double = 0.0, val damage: Double = 1.0,
    val armour: Double = 0.0, val evasion: Double = 0.0, val accuracy: Double = 40.0,
    val criticalChance: Double = 5.0, val regeneration: Double = 0.0,
    val manaRegeneration: Double = 0.0, val resistances: Map<String, Double> = emptyMap()
)
@Serializable data class Monster(
    val id: String, val name: String, val life: Double, val damage: Double,
    val armour: Double = 0.0, val evasion: Double = 0.0, val element: String = "physical",
    val lootTableId: String, val experience: Int, val gold: Int, val boss: Boolean = false,
    /** Иконка монстра. Заполняется при отдаче, в базе не хранится. */
    val icon: String? = null
)
@Serializable data class Zone(
    val id: String, val name: String, val description: String, val level: Int,
    val monsters: List<Monster>, val boss: Monster, val killsForBoss: Int = 3,
    /** Иконка зоны. Заполняется при отдаче, в базе не хранится. */
    val icon: String? = null
)
@Serializable data class LootEntry(val kind: String, val weight: Int, val amount: Int = 1)
@Serializable data class LootTable(val id: String, val rolls: Int, val entries: List<LootEntry>)
@Serializable data class CombatCatalog(val revision: Int = 1, val zones: List<Zone>, val lootTables: List<LootTable>)
@Serializable data class BattleReward(val name: String, val amount: Long, val itemId: String = "", val equipmentUuid: String = "")
@Serializable data class Battle(
    val id: String, val zoneId: String, val level: Int, val monster: Monster,
    val hero: Combatant, val enemy: Combatant, val turn: Int = 0,
    val status: BattleStatus = BattleStatus.ACTIVE, val potions: Int = 2,
    val log: List<String> = emptyList(), val rewards: List<BattleReward> = emptyList(),
    val lootTable: LootTable, val unsupportedStats: List<String> = emptyList()
)
@Serializable data class BattleView(val characterVersion: Long, val battle: Battle? = null, val zoneKills: Map<String, Int> = emptyMap(), val characterLevel: Int = 1, val experience: Double = 0.0, val gold: Long = 0)
@Serializable data class StartBattleCommand(val expectedVersion: Long, val requestId: String, val zoneId: String, val boss: Boolean = false)
@Serializable data class BattleActionCommand(val expectedVersion: Long, val requestId: String, val battleId: String, val action: BattleAction)
