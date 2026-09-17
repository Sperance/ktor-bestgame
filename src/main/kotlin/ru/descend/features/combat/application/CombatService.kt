package ru.descend.features.combat.application

import kotlin.random.Random
import kotlinx.serialization.encodeToString
import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterItems
import ru.descend.features.character.application.EquipmentService
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.combat.domain.*
import ru.descend.features.combat.persistence.*
import ru.descend.features.poe.catalog.*
import ru.descend.features.poe.domain.*
import ru.descend.features.poe.persistence.MongoModifierCatalog
import ru.descend.infrastructure.http.AppJson
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.http.*

class CombatService(private val characters: CharacterRepository, private val equipment: EquipmentService,
    private val catalogs: MongoModifierCatalog, private val worlds: CombatWorldRepository,
    private val receipts: BattleReceiptRepository,
    private val equipmentItems: ru.descend.features.character.persistence.CharacterEquipmentRepository) {
    suspend fun catalog() = worlds.catalog()
    private fun own(character: Character, actor: Actor) {
        if (character.deleted || character.userId != actor.id) missing()
    }
    suspend fun current(id: String, actor: Actor): BattleView {
        val c = characters.findById(checkedId(id)) ?: missing()
        own(c, actor)
        return BattleView(c.version, c.battle, c.zoneKills, c.level.toInt(), c.experience, c.money)
    }
    suspend fun start(id: String, actor: Actor, command: StartBattleCommand): BattleView {
        val world = catalog()
        return execute(id, actor, command.expectedVersion, command.requestId, "start:" + AppJson.encodeToString(command)) { c, session ->
            if (c.battle?.status == BattleStatus.ACTIVE) invalid("Finish the current battle first")
            val zone = world.zones.singleOrNull { it.id == command.zoneId } ?: invalid("Unknown zone")
            if (c.level < zone.level) invalid("Character level is too low")
            // Добыча больше не конкурирует за место в документе персонажа: слоты резервировать незачем.
            if (command.boss && (c.zoneKills[zone.id] ?: 0) < zone.killsForBoss) invalid("Defeat more monsters to unlock the boss")
            val monster = if (command.boss) zone.boss else zone.monsters.random()
            val stats = equipment.stats(c, session)
            fun value(key: String, fallback: Double = 0.0) = (stats.values[key] ?: fallback).coerceAtLeast(0.0)
            val weapon = stats.weapons.values.maxByOrNull { it.dps }
            val hero = Combatant(c.name, value("maximum_life", 60.0), value("maximum_life", 60.0),
                value("maximum_mana"), value("maximum_mana"), value("energy_shield"),
                (weapon?.dps ?: value("unarmed_dps", 10.0)).coerceAtLeast(1.0),
                value("armour"), value("evasion"), weapon?.accuracy ?: value("accuracy", 40.0),
                weapon?.criticalChance ?: (5.0 * value("critical_chance_multiplier", 1.0)), value("life_regeneration"), value("mana_regeneration"),
                listOf("fire", "cold", "lightning", "chaos").associateWith { stats.values[it + "_resistance"] ?: 0.0 })
            val enemy = Combatant(monster.name, monster.life, monster.life, damage = monster.damage,
                armour = monster.armour, evasion = monster.evasion, accuracy = 40.0 + zone.level * 8)
            c.copy(battle = Battle(command.requestId, zone.id, zone.level, monster, hero, enemy,
                log = listOf("Вы встретили ${monster.name}. Характеристики зафиксированы на начало боя."),
                lootTable = world.lootTables.single { it.id == monster.lootTableId }, unsupportedStats = stats.unsupported))
        }
    }
    suspend fun act(id: String, actor: Actor, request: BattleActionCommand): BattleView =
        execute(id, actor, request.expectedVersion, request.requestId, "act:" + AppJson.encodeToString(request)) { c, session ->
            val old = c.battle ?: invalid("No battle")
            if (old.id != request.battleId) conflict()
            if (old.status != BattleStatus.ACTIVE) invalid("Battle has ended")
            if (request.action == BattleAction.POWER && old.hero.mana < 8) invalid("Not enough mana")
            if (request.action == BattleAction.POTION && old.potions <= 0) invalid("No potions left")
            val battle = BattleEngine().act(old, request.action)
            val next = c.copy(battle = battle)
            if (battle.status == BattleStatus.VICTORY) reward(next, battle, session) else next
        }

    private suspend fun execute(id: String, actor: Actor, expected: Long, requestId: String, payload: String,
        transition: suspend (Character, com.mongodb.kotlin.client.coroutine.ClientSession) -> Character): BattleView {
        checkedId(id)
        if (!requestId.matches(Regex("[A-Za-z0-9_-]{8,80}"))) invalid("Invalid requestId")
        return transactionExecute("combat.command", retryTransientErrors = true) { session ->
            val old = characters.findById(id, session) ?: missing()
            own(old, actor)
            val key = PoeCatalog.stableId("combat:${actor.id}:$id:$requestId")
            receipts.findById(key, session)?.let {
                if (it.payload != payload) invalid("requestId already used for another command")
                return@transactionExecute it.result
            }
            checkVersion(old.version, expected)
            val next = transition(equipment.hydrate(old, session = session), session)
            characters.update(next, session)
            val result = BattleView(next.version, next.battle, next.zoneKills, next.level.toInt(), next.experience, next.money)
            receipts.insert(BattleReceipt(key, payload, result), session)
            result
        }
    }

    private suspend fun reward(c: Character, battle: Battle, session: com.mongodb.kotlin.client.coroutine.ClientSession): Character {
        val catalog = catalogs.snapshot().catalog
        val crafting = PoeCrafting(catalog)
        val inventory = PoeInventory(catalog, crafting)
        // Выпавшая экипировка уходит отдельными документами, поэтому лимита на инвентарь нет.
        val granted = mutableListOf<ru.descend.features.character.model.CharacterEquipments>()
        val items = c.items.map { it.copy() }.toMutableList()
        val rewards = mutableListOf(BattleReward("Опыт", battle.monster.experience.toLong()), BattleReward("Золото", battle.monster.gold.toLong()))
        repeat(battle.lootTable.rolls) {
            var ticket = Random.nextInt(battle.lootTable.entries.sumOf { it.weight })
            val entry = battle.lootTable.entries.first { ticket -= it.weight; ticket < 0 }
            when (entry.kind) {
                "NONE" -> Unit
                "NORMAL", "MAGIC", "RARE", "UNIQUE" -> {
                    val unique = entry.kind == "UNIQUE"
                    var pool = catalog.bases.filterValues { catalog.ordinaryDrop(it) && it.int("drop_level", 1) <= battle.level && catalog.unique(it) == unique }
                    // If no eligible unique exists at this level, explicitly fall back to rare.
                    val rarity = if (unique && pool.isEmpty()) {
                        pool = catalog.bases.filterValues { catalog.ordinaryDrop(it) && !catalog.unique(it) && it.int("drop_level", 1) <= battle.level }
                        PoeRarity.RARE
                    } else PoeRarity.valueOf(entry.kind)
                    require(pool.isNotEmpty()) { "No eligible equipment in the active catalog" }
                    val base = pool.keys.random()
                    val template = catalog.equipment(base)
                    val item = inventory.fromState(crafting.generate(base, battle.level, rarity, template.stockModifierDefinitionRefs))
                    granted += item
                    rewards += BattleReward("${template.name} · ${rarity.name}", 1, item.equipmentId, item.uuid)
                }
                else -> {
                    val currency = PoeCurrency.valueOf(entry.kind)
                    val itemId = inventory.currencyId(currency)
                    val existing = items.singleOrNull { it.itemId == itemId }
                    if (existing == null) items += CharacterItems(itemId, entry.amount.toLong())
                    else existing.amount = Math.addExact(existing.amount, entry.amount.toLong())
                    rewards += BattleReward(currency.displayName, entry.amount.toLong(), itemId)
                }
            }
        }
        val experience = c.experience + battle.monster.experience
        var level = c.level.toInt()
        while (level < 100 && experience >= 50.0 * level * (level + 1)) level++
        val kills = c.zoneKills + (battle.zoneId to if (battle.monster.boss) 0 else ((c.zoneKills[battle.zoneId] ?: 0) + 1).coerceAtMost(100))
        equipmentItems.addAll(c._id, granted, session)
        return c.copy(battle = battle.copy(rewards = rewards), zoneKills = kills, items = items,
            money = Math.addExact(c.money, battle.monster.gold.toLong()), experience = experience, level = level.toShort())
    }
}
