package features.logic.inventory

import base.exception.BaseRouteExceptions
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SlotGroup
import com.sperance.exileforge.rules.content.AutoSell
import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import base.exception.model.SkillTreeExceptions
import com.sperance.exileforge.rules.content.BenchRecipe
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.EquipSlots
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.SkillNodeType
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TreeAllocation
import com.sperance.exileforge.rules.roll.Bench
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.OrbApplier
import com.sperance.exileforge.rules.roll.OrbOutcome
import com.sperance.exileforge.rules.sheet.Requirements
import com.sperance.exileforge.rules.sheet.SellPrice
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.hero.Stash
import features.logic.hero.StashState
import features.logic.hero.sheetOf
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Ответ на сферу, эссенцию и верстак: ключ сообщения с аргументами, копия после и созданная копия (Зеркало). */
@Serializable
data class CurrencyApplyResponse(val messageKey: String, val messageArgs: List<String>, val item: ItemInstance, val created: ItemInstance? = null) {
    companion object {
        fun of(outcome: OrbOutcome) = CurrencyApplyResponse(outcome.messageKey, outcome.messageArgs, outcome.item, outcome.created)
    }
}

/** Чем кончилась продажа торговцу: копии больше нет, наружу - код, цена и золото героя. */
@Serializable
data class SellOutcome(val itemId: String, val code: String, val gold: Long, val money: Long)

/**
 * Вещи героя: надеть, снять, вставить в гнездо, продать, сферы, эссенции и верстак. Правила - в `rules`,
 * здесь - документ героя и одна запись на команду: списание и результат не расходятся.
 */
class InventoryService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val orbs by lazy { OrbApplier(index) }
    private val bench by lazy { Bench(index) }

    private fun template(item: ItemInstance, method: String): ItemTemplate =
        index.template(item.template) ?: throw CharacterExceptions.funExceptionEquipmentNotFound(method, item.template)

    /**
     * Надевает вещь в её слот, снимая то, что этот слот занимает и с чем она не носится (двуручное
     * освобождает обе руки, лук берёт колчан вместо щита). Требования проверяются по листу с уже
     * работающей экипировкой; надетое при их потере не слетает, а перестаёт работать.
     */
    suspend fun equip(heroId: String, itemId: String, requested: Slot?): ItemInstance {
        val method = "equip"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        val template = template(item, method)
        if (template.slot == Slot.MAP) throw CharacterExceptions.funExceptionMapNotWorn(method, template.code)
        if (template.slot == Slot.JEWEL) throw CharacterExceptions.funExceptionJewelNotWorn(method, template.code)
        val unmet = Requirements.unmet(template, hero.level, index.sheetOf(hero).stats)
        if (unmet.isNotEmpty()) throw CharacterExceptions.funExceptionRequirements(method, "${template.code}: ${unmet.joinToString()}")
        val worn = hero.items.filter { it.id != item.id && it.slot != null && !it.socketed }
        val target = EquipSlots.target(template.slot, requested, worn.mapNotNull { it.slot })
        val wornWeapon = worn.firstOrNull { it.slot == Slot.WEAPON_1H }?.let { index.template(it.template)?.weaponType }
        val freed = EquipSlots.displaced(target, template.weaponType, wornWeapon) + target
        worn.filter { it.slot in freed }.forEach { it.slot = null }
        item.slot = target
        heroes.save(hero, method)
        return item
    }

    suspend fun unequip(heroId: String, itemId: String): ItemInstance {
        val hero = heroes.requireHero(heroId, "unequip")
        val item = hero.requireItem(itemId, "unequip")
        item.slot = null
        item.socket = null
        heroes.save(hero, "unequip")
        return item
    }

    /** Самоцвет в гнездо дерева: гнездо должно быть взято героем и свободно, уникальный самоцвет - не второй такой же (1.31.0). */
    suspend fun socket(heroId: String, itemId: String, nodeCode: String): ItemInstance {
        val method = "socket"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        val template = template(item, method)
        if (template.slot != Slot.JEWEL) throw SkillTreeExceptions.funExceptionNotJewel(method, template.code)
        val node = index.tree.node(nodeCode) ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)
        if (node.type != SkillNodeType.JEWEL_SOCKET) throw SkillTreeExceptions.funExceptionNotSocket(method, nodeCode)
        if (hero.tree.none { it.code == nodeCode }) throw SkillTreeExceptions.funExceptionNotTaken(method, nodeCode)
        if (hero.items.any { it.socket == nodeCode && it.id != item.id }) throw SkillTreeExceptions.funExceptionSocketBusy(method, nodeCode)
        TreeAllocation.requireUniqueJewelFree(template, hero.items.filter { it.socketed && it.id != item.id }.map { it.template })
        item.slot = Slot.JEWEL
        item.socket = nodeCode
        heroes.save(hero, method)
        return item
    }

    suspend fun unsocket(heroId: String, itemId: String): ItemInstance = unequip(heroId, itemId)

    /** Продажа торговцу: цену назначает правило, надетое и вставленное сначала снимают. */
    suspend fun sell(heroId: String, itemId: String): SellOutcome {
        val method = "sell"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        val template = template(item, method)
        if (item.socketed) throw CharacterExceptions.funExceptionSellSocketed(method, template.code)
        if (item.equipped) throw CharacterExceptions.funExceptionSellEquipped(method, template.code)
        if (item.locked) throw CharacterExceptions.funExceptionItemLocked(method, template.code)
        val gold = SellPrice.of(index, template, item, index.sheetOf(hero).stats)
        hero.items.remove(item)
        hero.gain(gold)
        hero.count(Counter.ITEMS_SOLD)
        heroes.save(hero, method)
        return SellOutcome(itemId, template.code, gold, hero.money)
    }

    /**
     * Замок на вещь (1.28.0): в тайнике, надетую или в переполнении. Запертую нельзя продать и выставить
     * на аукцион, и переполнение не продаёт её само; сферы и ремесло замок не держит.
     */
    /** Строка фильтра добычи (1.45.0): какие группы слотов редкости [rarity] торговец забирает сразу; пустые [groups] - никакие. */
    suspend fun autoSell(heroId: String, rarity: Rarity, groups: Set<SlotGroup>): AutoSell {
        val method = "autoSell"
        if (rarity !in AutoSell.SELLABLE) throw BaseRouteExceptions.funExceptionQuery(method, "rarity")
        val hero = heroes.requireHero(heroId, method)
        hero.autoSell = hero.autoSell.with(rarity, groups)
        return heroes.save(hero, method).autoSell
    }

    suspend fun lock(heroId: String, itemId: String, locked: Boolean): ItemInstance {
        val method = "lock"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.item(itemId) ?: hero.overflow.firstOrNull { it.id == itemId }
            ?: throw CharacterExceptions.funExceptionItemNotFound(method, itemId)
        if (item.locked == locked) return item
        val changed = item.copy(locked = locked)
        val at = hero.overflow.indexOfFirst { it === item }
        if (at >= 0) hero.overflow[at] = changed else hero.replace(changed)
        heroes.save(hero, method)
        return changed
    }

    /** Сфера [orbCode] на копию: списывается и применяется одной записью, отказ правила не съедает сферу. */
    suspend fun applyOrb(heroId: String, itemId: String, orbCode: String, omenCode: String? = null): CurrencyApplyResponse {
        val method = "applyOrb"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        val orb = index.orb(orbCode) ?: throw CurrencyExceptions.funExceptionNotCurrency(method, orbCode)
        // Знамение (1.35.0) тратится той же записью, что и сфера.
        val omen = omenCode?.takeIf { it.isNotBlank() }?.let { Omen.of(it) ?: throw CurrencyExceptions.funExceptionNotCurrency(method, it) }
        hero.spend(orbCode, 1, method)
        omen?.let { hero.spend(it.code, 1, method) }
        hero.count(Counter.ORBS_USED)
        if (orb == Orb.MIRROR_OF_KALANDRA) hero.count(Counter.MIRRORS)
        return finish(hero, orbs.apply(orb, item, template(item, method), Dice.system(), omen) { Hero.newItemId() }, method)
    }

    /** Выбор [choice] из вариантов, что предложила сфера раскрытия (1.35.0): бесплатно, одной записью. */
    suspend fun unveil(heroId: String, itemId: String, choice: Int): CurrencyApplyResponse {
        val method = "unveil"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        return finish(hero, orbs.reveal(item, template(item, method), choice), method)
    }

    /** Эссенция [essenceCode] на копию - так же, одной записью со списанием. */
    suspend fun applyEssence(heroId: String, itemId: String, essenceCode: String): CurrencyApplyResponse {
        val method = "applyEssence"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        val essence = index.essence(essenceCode) ?: throw CurrencyExceptions.funExceptionNotCurrency(method, essenceCode)
        hero.spend(essenceCode, 1, method)
        hero.count(Counter.ESSENCES_USED)
        return finish(hero, orbs.applyEssence(essence, item, template(item, method), Dice.system()), method)
    }

    /** Рецепты верстака, известные герою: остальные скрыты, их находят на картах. */
    fun bench(hero: Hero): List<BenchRecipe> = hero.recipes.mapNotNull(index::recipe)

    suspend fun craft(heroId: String, itemId: String, recipeCode: String): CurrencyApplyResponse {
        val method = "craft"
        val recipe = index.recipe(recipeCode) ?: throw CurrencyExceptions.funExceptionRecipeNotFound(method, recipeCode)
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        hero.spend(recipe.orb.name, recipe.amount, method)
        return finish(hero, bench.craft(item, template(item, method), recipe, hero.recipes, Dice.system()), method)
    }

    suspend fun uncraft(heroId: String, itemId: String): CurrencyApplyResponse {
        val method = "uncraft"
        val hero = heroes.requireHero(heroId, method)
        val item = hero.requireItem(itemId, method)
        hero.spend(index.rules.bench.uncraftOrb.name, 1, method)
        return finish(hero, bench.uncraft(item, template(item, method)), method)
    }

    /** Места тайника героя и его переполнение. */
    suspend fun stash(heroId: String): StashState = Stash.state(heroes.requireHero(heroId, "stash"), index)

    /** Докупить пачку мест тайника за золото. */
    suspend fun expandStash(heroId: String): StashState = stashCommand(heroId, "stashExpand") { Stash.expand(it, index) }

    /** Забрать из переполнения вещь [itemId] или, без неё, всё, что влезет. */
    suspend fun claimOverflow(heroId: String, itemId: String?): StashState = stashCommand(heroId, "stashClaim") { Stash.claim(it, itemId, index) }

    /** Продать вещь из переполнения, не забирая её в тайник. */
    suspend fun sellOverflow(heroId: String, itemId: String): StashState = stashCommand(heroId, "stashSell") { Stash.sellOverflow(it, itemId, index) }

    private suspend fun stashCommand(heroId: String, method: String, command: (Hero) -> Any): StashState {
        val hero = heroes.requireHero(heroId, method)
        command(hero)
        heroes.save(hero, method)
        return Stash.state(hero, index)
    }

    private suspend fun finish(hero: Hero, outcome: OrbOutcome, method: String): CurrencyApplyResponse {
        hero.replace(outcome.item)
        outcome.created?.let { Stash.receive(hero, it, index) }
        heroes.save(hero, method)
        return CurrencyApplyResponse.of(outcome)
    }
}
