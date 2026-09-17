package ru.descend.features.passives.domain

import ru.descend.features.passives.model.*
import ru.descend.domain.modifiers.*
import ru.descend.domain.stats.StatId
import ru.descend.shared.http.invalid

class PassiveRules(val tree: PassiveTree) {
    val nodes = tree.nodes.associateBy { it.id }
    private val links = tree.nodes.associate { it.id to mutableSetOf<String>() }
    init {
        require(tree.revision > 0 && tree.nodes.size in 1..500 && nodes.size == tree.nodes.size && tree.rootId in nodes)
        require(tree.edges.size <= 2000)
        tree.nodes.forEach { node ->
            require(node.id.matches(Regex("[a-z0-9_]{1,80}")) && node.cost in 1..10 && node.name.length in 1..100)
            require(node.x.isFinite() && node.y.isFinite() && node.x in -2000.0..2000.0 && node.y in -2000.0..2000.0)
            require(node.effects.isNotEmpty() && node.effects.size <= 10)
            node.effects.forEach { e ->
                require(e.stat in supported && e.value.isFinite() && e.value > 0)
                require(if (e.operation == PassiveOperation.FLAT) e.value <= 1000 else e.value < 1)
            }
        }
        tree.edges.forEach { edge ->
            require(edge.from != edge.to && edge.from in nodes && edge.to in nodes)
            links.getValue(edge.from) += edge.to; links.getValue(edge.to) += edge.from
        }
        require(connected(nodes.keys)) { "Tree contains unreachable nodes" }
    }
    fun budget(level: Int) = (level - 1).coerceIn(0, 99)
    fun cost(selected: Set<String>): Int = selected.sumOf { nodes[it]?.cost ?: invalid("Unknown passive node") }
    fun connected(selected: Set<String>): Boolean {
        if (selected.isEmpty()) return true
        if (tree.rootId !in selected || !nodes.keys.containsAll(selected)) return false
        val visited = mutableSetOf(tree.rootId)
        val queue = ArrayDeque<String>(); queue.add(tree.rootId)
        while (queue.isNotEmpty()) links.getValue(queue.removeFirst()).filter { it in selected && visited.add(it) }.forEach(queue::addLast)
        return visited == selected
    }
    fun validate(selected: Set<String>, level: Int) {
        if (!connected(selected)) invalid("All selected nodes must connect to the starting node")
        if (cost(selected) > budget(level)) invalid("Not enough passive points")
    }
    fun allocatable(selected: Set<String>, level: Int) = nodes.keys.filterTo(mutableSetOf()) {
        it !in selected && cost(selected) + nodes.getValue(it).cost <= budget(level) && connected(selected + it)
    }
    fun refundable(selected: Set<String>) = selected.filterTo(mutableSetOf()) { connected(selected - it) }
    fun transition(selected: Set<String>, level: Int, action: PassiveAction, id: String?): Set<String> {
        validate(selected, level)
        val next = when (action) {
            PassiveAction.ALLOCATE -> { if (id == null || id in selected || id !in nodes) invalid("Node cannot be allocated"); selected + id }
            PassiveAction.REFUND -> { if (id == null || id !in selected) invalid("Node is not allocated"); selected - id }
            PassiveAction.RESET -> { if (id != null) invalid("Reset must not specify a node"); emptySet() }
        }
        validate(next, level)
        return next
    }
    fun modifiers(selected: Set<String>): List<ResolvedModifier> = selected.sorted().map { id ->
        val node = nodes[id] ?: invalid("Unknown passive node")
        ResolvedModifier("passive:$id", 1, ModifierSource.PASSIVE, node.effects.map { e ->
            ModifierEffect.Stat(StatId(e.stat), ModifierOperation.valueOf(e.operation.name), ValueExpression.Constant(e.value))
        }, 0, scope = ModifierScope.CHARACTER)
    }
    companion object {
        val supported = setOf("strength", "dexterity", "intelligence", "maximum_life", "maximum_mana", "armour", "evasion", "energy_shield",
            "accuracy", "life_regeneration", "mana_regeneration", "fire_resistance", "cold_resistance", "lightning_resistance", "chaos_resistance",
            "attack_damage_multiplier", "attack_speed_multiplier", "critical_chance_multiplier")
    }
}
