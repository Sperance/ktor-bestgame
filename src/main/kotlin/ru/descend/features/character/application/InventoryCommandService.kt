package ru.descend.features.character.application

import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.datetime.LocalDateTime
import ru.descend.features.character.model.*
import ru.descend.features.character.persistence.CharacterInventoryRepository
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.recipe.persistence.RecipeRepository
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.extensions.now
import ru.descend.shared.http.*
import kotlin.random.Random

/**
 * Предметы персонажа хранятся без стаков: каждая единица — свой документ. Поэтому «изменить
 * количество» здесь не пересчёт поля `amount`, а вставка или удаление нужного числа документов
 * в той же транзакции, что и bump версии персонажа.
 */
class InventoryCommandService(private val equipment: EquipmentService, private val items: ItemsRepository,
    private val recipes: RecipeRepository, private val redemptions: RedemptionCodesRepository,
    private val inventory: CharacterInventoryRepository) {
    private fun quantity(value: Double): Long {
        if (!value.isFinite() || value < 1 || value > CharacterInventoryRepository.MAX_UNITS_PER_COMMAND || value != value.toLong().toDouble())
            invalid("Expected a whole item quantity of 1 to ${CharacterInventoryRepository.MAX_UNITS_PER_COMMAND}")
        return value.toLong()
    }
    private suspend fun adjust(character: Character, deltas: List<ItemDelta>, session: ClientSession): Character {
        if (deltas.size !in 1..100 || deltas.map { it.itemId }.toSet().size != deltas.size) invalid("Invalid or duplicate item list")
        // Сначала полная проверка, потом записи: неизвестный предмет не должен оставить после себя
        // вставок, которые придётся откатывать транзакцией.
        deltas.forEach {
            if (it.amount == 0L) invalid("Invalid item quantity")
            items.findById(checkedId(it.itemId), session)?.takeUnless { item -> item.deleted } ?: missing()
        }
        // Единицы — это документы, и все они пишутся одной транзакцией: ограничение общее на команду.
        inventory.checkedAmount(deltas.fold(0L) { sum, delta -> Math.addExact(sum, Math.abs(delta.amount)) })
        for (delta in deltas) {
            // Отрицательная дельта списывает ровно столько единиц, положительная — столько создаёт.
            if (delta.amount > 0) inventory.grant(character._id, delta.itemId, delta.amount, session)
            else inventory.consume(character._id, delta.itemId, -delta.amount, session)
        }
        return character
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
        val deltas = redemption.treasure.groupBy { it.itemId }.map { (itemId, rewards) -> ItemDelta(itemId, rewards.fold(0L) { sum, r -> Math.addExact(sum, quantity(r.amount)) }) }
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
        val debited = if (changes.isEmpty()) c else adjust(c, changes.map { ItemDelta(it.key, it.value) }, s)
        val rewards = mutableMapOf<String, Long>()
        for (output in recipe.arrayOut) {
            if (!output.chance.isFinite() || output.chance !in 0.0..1.0) invalid("Invalid output chance")
            repeat(command.amount.toInt()) {
                if (Random.nextDouble() < output.chance) rewards[output.itemId] = Math.addExact(rewards[output.itemId] ?: 0, quantity(output.amount))
            }
        }
        val next = if (rewards.isEmpty()) debited else adjust(debited, rewards.map { ItemDelta(it.key, it.value) }, s)
        recipe.globalUses = Math.addExact(recipe.globalUses, command.amount)
        recipes.update(recipe, s)
        next
    }
}
