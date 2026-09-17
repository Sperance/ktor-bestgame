package features.poe

import kotlin.random.Random
import kotlin.test.*
import ru.descend.features.combat.domain.*

class BattleEngineTest {
    private fun battle() = CombatWorld.initial.zones.first().let { zone ->
        Battle("test-battle", zone.id, 1, zone.monsters.first(),
            Combatant("Hero", 100.0, 100.0, 20.0, 20.0, damage = 10.0, accuracy = 10000.0),
            Combatant("Enemy", 1000.0, 1000.0, damage = 20.0, accuracy = 10000.0),
            lootTable = CombatWorld.initial.lootTables.first())
    }
    @Test fun guardReducesDamageAndRestoresMana() {
        val original = battle().copy(hero = battle().hero.copy(mana = 0.0))
        val guarded = BattleEngine(Random(42)).act(original, BattleAction.GUARD)
        val potion = BattleEngine(Random(42)).act(original, BattleAction.POTION)
        assertTrue(guarded.hero.life > potion.hero.life)
        assertEquals(6.0, guarded.hero.mana)
        assertEquals(1, guarded.turn)
        assertEquals(0, original.turn)
    }
    @Test fun powerfulAttackConsumesManaAndImprovesDamage() {
        val ordinary = BattleEngine(Random(10)).act(battle(), BattleAction.ATTACK)
        val power = BattleEngine(Random(10)).act(battle(), BattleAction.POWER)
        assertTrue(power.enemy.life < ordinary.enemy.life)
        assertEquals(12.0, power.hero.mana)
        assertFailsWith<IllegalArgumentException> { BattleEngine().act(battle().copy(hero = battle().hero.copy(mana = 0.0)), BattleAction.POWER) }
    }
    @Test fun killingBlowPreventsRetaliationAndTerminalActionsAreRejected() {
        val won = BattleEngine(Random(10)).act(battle().copy(enemy = battle().enemy.copy(life = 1.0)), BattleAction.POWER)
        assertEquals(BattleStatus.VICTORY, won.status)
        assertEquals(100.0, won.hero.life)
        assertFailsWith<IllegalArgumentException> { BattleEngine().act(won, BattleAction.POTION) }
    }
    @Test fun shieldAndResistanceProtectAgainstElementalDamage() {
        val initial = battle().copy(monster = battle().monster.copy(element = "fire"))
        val protected = initial.copy(hero = initial.hero.copy(shield = 100.0, resistances = mapOf("fire" to 75.0)))
        val result = BattleEngine(Random(42)).act(protected, BattleAction.GUARD)
        assertEquals(100.0, result.hero.life)
        assertTrue(result.hero.shield < 100.0)
        val withoutResistance = BattleEngine(Random(42)).act(protected.copy(hero = protected.hero.copy(resistances = emptyMap())), BattleAction.GUARD)
        assertTrue(result.hero.shield > withoutResistance.hero.shield)
    }
    @Test fun potionsAndTurnLimitAreBounded() {
        assertFailsWith<IllegalArgumentException> { BattleEngine().act(battle().copy(potions = 0), BattleAction.POTION) }
        assertEquals(BattleStatus.FLED, BattleEngine().act(battle().copy(turn = 79), BattleAction.GUARD).status)
        val fled = BattleEngine().act(battle(), BattleAction.FLEE)
        assertTrue(fled.rewards.isEmpty())
        assertEquals(BattleStatus.FLED, fled.status)
    }
    @Test fun worldRejectsBrokenLootAndInvalidEnemyStats() {
        CombatWorld.validate(CombatWorld.initial)
        assertFailsWith<IllegalArgumentException> { CombatWorld.validate(CombatWorld.initial.copy(lootTables = emptyList())) }
        val zone = CombatWorld.initial.zones.first()
        val broken = zone.copy(monsters = listOf(zone.monsters.first().copy(damage = Double.NaN)))
        assertFailsWith<IllegalArgumentException> { CombatWorld.validate(CombatWorld.initial.copy(zones = listOf(broken))) }
    }
}
