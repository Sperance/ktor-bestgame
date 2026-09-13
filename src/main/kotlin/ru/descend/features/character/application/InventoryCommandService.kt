package ru.descend.features.character.application

import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.datetime.LocalDateTime
import ru.descend.features.character.model.*
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.recipe.persistence.RecipeRepository
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.extensions.now
import ru.descend.shared.http.*
import kotlin.random.Random

class InventoryCommandService(private val equipment: EquipmentService, private val items: ItemsRepository,
    private val recipes: RecipeRepository, private val redemptions: RedemptionCodesRepository) {
    private fun quantity(value: Double): Long {
        if (!value.isFinite() || value < 1 || value > 1_000_000_000 || value != value.toLong().toDouble()) invalid("Expected a positive whole item quantity")
        return value.toLong()
    }
    private suspend fun adjust(character: Character, deltas: List<CharacterItems>, session: ClientSession): Character {
        if (deltas.size !in 1..100 || deltas.map { it.itemId }.toSet().size != deltas.size) invalid("Invalid or duplicate item list")
        if (character.items.map { it.itemId }.toSet().size != character.items.size) invalid("Inventory contains duplicate stacks")
        val stacks = character.items.associate { it.itemId to it.amount }.toMutableMap()
        for (delta in deltas) {
            items.findById(checkedId(delta.itemId), session)?.takeUnless { it.deleted } ?: missing()
            if (delta.amount !in -1_000_000_000L..1_000_000_000L) invalid("Invalid item quantity")
            val next = Math.addExact(stacks[delta.itemId] ?: 0, delta.amount)
            if (next < 0) invalid("Not enough items")
            stacks[delta.itemId] = next
        }
        if (stacks.size > 500) invalid("Inventory is full")
        return character.copy(items = stacks.filterValues { it > 0 }.map { CharacterItems(it.key, it.value) }.toMutableList())
    }
    suspend fun adjust(id: String, actor: Actor, command: AdjustItemsCommand): EquipmentView {
        actor.requireAdmin()
        return equipment.change(id, actor, command.expectedVersion) { c, s -> adjust(c, command.items, s) }
    }
    suspend fun redeem(id: String, actor: Actor, command: RedeemCommand): EquipmentView = equipment.change(id, actor, command.expectedVersion) { c, s ->
        if (command.code.length !in 1..100) invalid("Invalid redemption code")
        val redemption = redemptions.findByField(ru.descend.features.redemptioncodes.model.RedemptionCodes::code, command.code, s)?.takeUnless { it.deleted } ?: missing()
        if (redemption.expiredAt?.let { it < LocalDateTime.now() } == true) invalid("Code expired")
        if (c.gainedRedemptionCodes.any { it.redemptionCodeId == redemption._id }) invalid("Code already redeemed")
        val deltas = redemption.treasure.groupBy { it.itemId }.map { (itemId, rewards) -> CharacterItems(itemId, rewards.fold(0L) { sum, r -> Math.addExact(sum, quantity(r.amount)) }) }
        val next = if (deltas.isEmpty()) c else adjust(c, deltas, s)
        redemption.used = Math.addExact(redemption.used, 1)
        redemptions.update(redemption, s)
        next.copy(gainedRedemptionCodes = (c.gainedRedemptionCodes + GainedRedemtionCodes(redemption._id, LocalDateTime.now())).toMutableList())
    }
    suspend fun recipe(id: String, actor: Actor, command: UseRecipeCommand): EquipmentView = equipment.change(id, actor, command.expectedVersion) { c, s ->
        if (command.amount !in 1..100 || command.ingredientIds.size > 100 || command.ingredientIds.toSet().size != command.ingredientIds.size) invalid("Invalid recipe quantity or ingredient list")
        val recipe = recipes.findById(checkedId(command.recipeId), s)?.takeUnless { it.deleted } ?: missing()
        checkVersion(recipe.version, command.recipeVersion)
        if (recipe.needOpenRecipe && recipe._id !in c.recipeAccess) forbidden()
        // The old route ignored both work time and requirements. Unsupported execution modes now fail closed.
        if (recipe.timeWork != 0.0 || !recipe.requirement.isNullOrEmpty()) invalid("This endpoint supports only instantaneous recipes without skill requirements")
        val selected = command.ingredientIds.map { items.findById(checkedId(it), s)?.takeUnless { item -> item.deleted } ?: missing() }
        val changes = mutableMapOf<String, Long>()
        for (input in recipe.arrayIn) {
            if (input.countCorrect() != 1) invalid("Invalid recipe input selector")
            val matches = selected.filter { item -> input.itemId?.let { it == item._id } ?: input.category?.let { it == item.category } ?: (input.subCategory == item.subCategory) }
            if (matches.size != 1) invalid("Select exactly one ingredient for each input")
            val key = matches.single()._id
            changes[key] = Math.subtractExact(changes[key] ?: 0L, Math.multiplyExact(quantity(input.amount), command.amount))
        }
        if (changes.keys != command.ingredientIds.toSet()) invalid("Unexpected ingredients")
        // Validate/debit inputs first, even if the recipe returns the same item.
        val debited = if (changes.isEmpty()) c else adjust(c, changes.map { CharacterItems(it.key, it.value) }, s)
        val rewards = mutableMapOf<String, Long>()
        for (output in recipe.arrayOut) {
            if (!output.chance.isFinite() || output.chance !in 0.0..1.0) invalid("Invalid output chance")
            repeat(command.amount.toInt()) {
                if (Random.nextDouble() < output.chance) rewards[output.itemId] = Math.addExact(rewards[output.itemId] ?: 0, quantity(output.amount))
            }
        }
        val next = if (rewards.isEmpty()) debited else adjust(debited, rewards.map { CharacterItems(it.key, it.value) }, s)
        recipe.globalUses = Math.addExact(recipe.globalUses, command.amount)
        recipes.update(recipe, s)
        next
    }
}
