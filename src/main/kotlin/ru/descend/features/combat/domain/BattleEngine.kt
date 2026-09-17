package ru.descend.features.combat.domain

import kotlin.random.Random
import kotlin.math.max

/** One turn is one second. Weapon DPS becomes a single attack packet in this text ruleset. */
class BattleEngine(private val random: Random = Random.Default) {
    fun act(battle: Battle, action: BattleAction): Battle {
        require(battle.status == BattleStatus.ACTIVE) { "Battle has ended" }
        if (action == BattleAction.FLEE) return battle.copy(status = BattleStatus.FLED, log = (battle.log + "Вы отступили. Наград нет.").takeLast(30))
        require(action != BattleAction.POWER || battle.hero.mana >= 8) { "Not enough mana" }
        require(action != BattleAction.POTION || battle.potions > 0) { "No potions left" }
        var hero = battle.hero
        var enemy = battle.enemy
        val lines = mutableListOf("Ход ${battle.turn + 1}")
        if (action == BattleAction.POTION) {
            hero = hero.copy(life = (hero.life + hero.maxLife * 0.4).coerceAtMost(hero.maxLife))
            lines += "Флакон восстановил здоровье."
        }
        if (action == BattleAction.GUARD) {
            hero = hero.copy(mana = (hero.mana + 6).coerceAtMost(hero.maxMana))
            lines += "Вы защищаетесь: входящий урон снижен на 65%."
        }
        if (action == BattleAction.ATTACK || action == BattleAction.POWER) {
            if (action == BattleAction.POWER) hero = hero.copy(mana = hero.mana - 8)
            val hit = strike(hero, enemy, "physical", if (action == BattleAction.POWER) 1.8 else 1.0)
            enemy = hit.first
            lines += "Вы: ${hit.second}."
        }
        var status = if (enemy.life <= 0) BattleStatus.VICTORY else BattleStatus.ACTIVE
        if (status == BattleStatus.ACTIVE) {
            // Boss telegraphs a heavy attack one turn in advance.
            val heavy = battle.monster.boss && (battle.turn + 1) % 3 == 0
            val hit = strike(enemy, hero, battle.monster.element, (if (heavy) 1.8 else 1.0) * if (action == BattleAction.GUARD) 0.35 else 1.0)
            hero = hit.first
            lines += "${enemy.name}${if (heavy) " — тяжёлый удар" else ""}: ${hit.second}."
            if (hero.life <= 0) status = BattleStatus.DEFEAT
            else {
                hero = hero.copy(life = (hero.life + hero.regeneration.coerceAtLeast(0.0)).coerceAtMost(hero.maxLife),
                    mana = (hero.mana + hero.manaRegeneration.coerceAtLeast(0.0)).coerceAtMost(hero.maxMana))
                if (battle.monster.boss && (battle.turn + 2) % 3 == 0) lines += "Босс готовит тяжёлый удар. Защищайтесь!"
            }
        }
        if (status == BattleStatus.ACTIVE && battle.turn + 1 >= 80) {
            status = BattleStatus.FLED
            lines += "Лимит 80 ходов: вы отступили без наград."
        }
        if (status == BattleStatus.VICTORY) lines += "Победа!"
        if (status == BattleStatus.DEFEAT) lines += "Поражение. Вы вернулись в лагерь без наград."
        return battle.copy(hero = hero, enemy = enemy, status = status, turn = battle.turn + 1,
            potions = battle.potions - if (action == BattleAction.POTION) 1 else 0,
            log = (battle.log + lines).takeLast(30))
    }

    private fun strike(attacker: Combatant, defender: Combatant, element: String, multiplier: Double): Pair<Combatant, String> {
        val chance = (attacker.accuracy / (attacker.accuracy + defender.evasion.coerceAtLeast(0.0) * 0.5 + 1)).coerceIn(0.1, 0.98)
        if (random.nextDouble() >= chance) return defender to "промах"
        val critical = random.nextDouble(100.0) < attacker.criticalChance.coerceIn(0.0, 100.0)
        val raw = attacker.damage.coerceAtLeast(1.0) * random.nextDouble(0.85, 1.15) * multiplier * if (critical) 1.5 else 1.0
        val reduction = if (element == "physical") (defender.armour.coerceAtLeast(0.0) / (defender.armour.coerceAtLeast(0.0) + 5 * raw)).coerceAtMost(0.9)
            else (defender.resistances[element] ?: 0.0).coerceIn(-200.0, 75.0) / 100
        val damage = max(1.0, raw * (1 - reduction))
        val absorbed = if (element == "chaos") 0.0 else minOf(defender.shield, damage)
        return defender.copy(shield = (defender.shield - absorbed).coerceAtLeast(0.0),
            life = (defender.life - (damage - absorbed)).coerceAtLeast(0.0)) to
            "${if (critical) "критический " else ""}урон ${damage.toInt()} ($element)"
    }
}
