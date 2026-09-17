package ru.descend.features.passives.domain

import kotlin.math.cos
import kotlin.math.sin
import ru.descend.features.passives.model.*

/** Original compact tree inspired by the connected passive graph mechanic. Stable IDs are permanent. */
object PassiveTreeSeed {
    private fun flat(stat: String, value: Double) = PassiveEffect(stat, PassiveOperation.FLAT, value)
    private fun increased(stat: String, value: Double) = PassiveEffect(stat, PassiveOperation.INCREASED, value)
    private data class Branch(val id: String, val name: String, val minor: PassiveEffect, val side: PassiveEffect,
        val major: List<PassiveEffect>, val keystoneName: String, val keystone: List<PassiveEffect>)
    val tree: PassiveTree by lazy {
        val branches = listOf(
            Branch("vitality", "Жизненная сила", flat("maximum_life", 8.0), flat("strength", 5.0),
                listOf(increased("maximum_life", .08), flat("life_regeneration", .5)), "Сердце титана",
                listOf(PassiveEffect("maximum_life", PassiveOperation.MORE, .2), PassiveEffect("maximum_mana", PassiveOperation.LESS, .15))),
            Branch("warfare", "Военное искусство", increased("attack_damage_multiplier", .04), flat("strength", 5.0),
                listOf(increased("attack_damage_multiplier", .1), increased("accuracy", .1)), "Ярость изгнанника",
                listOf(PassiveEffect("attack_damage_multiplier", PassiveOperation.MORE, .2), PassiveEffect("armour", PassiveOperation.LESS, .15))),
            Branch("agility", "Ловкость", increased("attack_speed_multiplier", .02), flat("dexterity", 5.0),
                listOf(increased("attack_speed_multiplier", .05), flat("evasion", 30.0)), "Танец клинков",
                listOf(PassiveEffect("attack_speed_multiplier", PassiveOperation.MORE, .15), PassiveEffect("maximum_life", PassiveOperation.LESS, .1))),
            Branch("wisdom", "Мудрость", flat("maximum_mana", 8.0), flat("intelligence", 5.0),
                listOf(increased("maximum_mana", .1), flat("mana_regeneration", .5)), "Разум над плотью",
                listOf(PassiveEffect("maximum_mana", PassiveOperation.MORE, .3), PassiveEffect("maximum_life", PassiveOperation.LESS, .1))),
            Branch("ward", "Защита", flat("energy_shield", 8.0), flat("armour", 20.0),
                listOf(increased("armour", .12), increased("energy_shield", .1)), "Несокрушимый оплот",
                listOf(PassiveEffect("armour", PassiveOperation.MORE, .3), PassiveEffect("attack_speed_multiplier", PassiveOperation.LESS, .1))),
            Branch("resilience", "Стойкость", flat("fire_resistance", 3.0), flat("cold_resistance", 3.0),
                listOf(flat("lightning_resistance", 8.0), flat("chaos_resistance", 5.0)), "Печать стихий",
                listOf(flat("fire_resistance", 12.0), flat("cold_resistance", 12.0), flat("lightning_resistance", 12.0), PassiveEffect("maximum_mana", PassiveOperation.LESS, .1)))
        )
        val nodes = mutableListOf(PassiveNode("origin", "Начало пути", "Общий старт для всех героев", PassiveNodeKind.ORIGIN, 0.0, 0.0, listOf(flat("maximum_life", 5.0))))
        val edges = mutableListOf<PassiveEdge>()
        branches.forEachIndexed { index, b ->
            val angle = index * Math.PI / 3 - Math.PI / 2
            for (step in 1..9) {
                val radius = 65.0 + step * 65.0
                val id = "${b.id}_$step"
                val notable = step % 3 == 0
                nodes += PassiveNode(id, "${b.name} $step", if (notable) "Крупный узел" else "Пассивный навык", if (notable) PassiveNodeKind.NOTABLE else PassiveNodeKind.SMALL,
                    cos(angle) * radius, sin(angle) * radius, if (notable) b.major else listOf(b.minor))
                edges += PassiveEdge(if (step == 1) "origin" else "${b.id}_${step - 1}", id)
                val sideAngle = angle + if (step % 2 == 0) .28 else -.28
                nodes += PassiveNode("${id}_side", "${b.name}: подготовка $step", "Боковая ветвь", PassiveNodeKind.SMALL,
                    cos(sideAngle) * radius, sin(sideAngle) * radius, listOf(b.side))
                edges += PassiveEdge(id, "${id}_side")
            }
            nodes += PassiveNode("${b.id}_keystone", b.keystoneName, "Ключевой узел: сильный бонус с ограничением", PassiveNodeKind.KEYSTONE,
                cos(angle) * 740, sin(angle) * 740, b.keystone)
            edges += PassiveEdge("${b.id}_9", "${b.id}_keystone")
            // Cross-links permit alternative routes. Refund validation must traverse the whole graph.
            edges += PassiveEdge("${b.id}_3", "${branches[(index + 1) % branches.size].id}_3")
        }
        PassiveTree(1, "Созвездие изгнанника", "origin", nodes, edges)
    }
}
