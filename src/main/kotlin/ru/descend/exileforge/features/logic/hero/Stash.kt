package ru.descend.exileforge.features.logic.hero
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.sheet.SellPrice
import kotlinx.serialization.Serializable
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.features.data.hero.Hero

/** Куда легли пришедшие вещи: в тайник, в переполнение или проданы торговцу сами - и за сколько. */
@Serializable
data class Received(val stashed: Int = 0, val overflowed: Int = 0, val sold: Int = 0, val gold: Long = 0) {
    operator fun plus(other: Received) = Received(stashed + other.stashed, overflowed + other.overflowed, sold + other.sold, gold + other.gold)
}

/** Места тайника героя: сколько занято, сколько всего, цена следующей пачки (ноль - потолок) и очередь переполнения. */
@Serializable
data class StashState(val used: Int, val capacity: Int, val max: Int, val price: Long, val overflow: Int, val overflowMax: Int, val money: Long)

/**
 * Тайник героя (1.1.0): копий не больше мест - база правил `stash` и докупленные пачки, потолок
 * [com.sperance.exileforge.rules.content.StashRules.HARD_CAP] считая надетое. Любая вещь, что приходит
 * герою - добыча, ремесло, торговец, аукцион, промокод, выдача, Зеркало, - идёт через [receive]: не
 * влезла в тайник - ждёт в переполнении, не влезла и туда - продаётся торговцу по его цене; запертая
 * ([ItemInstance.locked], 1.28.0) не продаётся и остаётся в переполнении.
 */
object Stash {
    fun capacity(hero: Hero, index: ContentIndex): Int = index.rules.stash.capacity(hero.stashSlots)

    fun state(hero: Hero, index: ContentIndex): StashState {
        val rules = index.rules.stash
        return StashState(hero.items.size, capacity(hero, index), rules.maxSlots, rules.price(hero.stashSlots), hero.overflow.size, rules.overflowSlots, hero.money)
    }

    fun receive(hero: Hero, item: ItemInstance, index: ContentIndex): Received = receive(hero, listOf(item), index)

    fun receive(hero: Hero, items: List<ItemInstance>, index: ContentIndex): Received {
        if (items.isEmpty()) return Received()
        val capacity = capacity(hero, index)
        val overflowMax = index.rules.stash.overflowSlots
        val taken = HashSet<String>().apply {
            hero.items.mapTo(this) { it.id }
            hero.overflow.mapTo(this) { it.id }
        }
        val sheet by lazy { index.sheetOf(hero).stats }
        var received = Received()
        items.forEach { incoming ->
            // Id копии обязан быть единственным у героя: иначе надеть, продать или выставить можно было бы не ту
            val item = if (taken.add(incoming.id)) incoming else incoming.copy(id = Hero.newItemId()).also { taken += it.id }
            received += when {
                hero.items.size < capacity -> {
                    hero.items += item
                    Received(stashed = 1)
                }

                // Запертую вещь торговец сам не забирает: она ждёт в переполнении и сверх его мест
                hero.overflow.size < overflowMax || item.locked -> {
                    hero.overflow += item
                    Received(overflowed = 1)
                }

                else -> {
                    val gold = index.template(item.template)?.let { SellPrice.of(index, it, item, sheet) } ?: 0L
                    hero.gain(gold)
                    hero.count(Counter.ITEMS_SOLD)
                    Received(sold = 1, gold = gold)
                }
            }
        }
        return received
    }

    /**
     * Возврат своей вещи (1.30.0) - снятый или истёкший лот аукциона: в тайник, а без места - в переполнение
     * сверх его мест. Торговцу своя вещь не уходит никогда.
     */
    fun giveBack(hero: Hero, item: ItemInstance, index: ContentIndex) {
        val own = if (hero.items.none { it.id == item.id } && hero.overflow.none { it.id == item.id }) item else item.copy(id = Hero.newItemId())
        if (hero.items.size < capacity(hero, index)) hero.items += own else hero.overflow += own
    }

    /** Забрать из переполнения в тайник вещь [itemId] или, без неё, столько по порядку, сколько влезет. */
    fun claim(hero: Hero, itemId: String?, index: ContentIndex): Int {
        val method = "stashClaim"
        val free = capacity(hero, index) - hero.items.size
        if (free <= 0) throw CharacterExceptions.funExceptionStashFull(method, hero.items.size.toString())
        val moving = if (itemId != null) {
            listOf(hero.overflow.firstOrNull { it.id == itemId } ?: throw CharacterExceptions.funExceptionItemNotFound(method, itemId))
        } else {
            hero.overflow.take(free)
        }
        hero.overflow.removeAll(moving.toSet())
        hero.items += moving
        return moving.size
    }

    /** Продать вещь из переполнения торговцу, не забирая её в тайник. */
    fun sellOverflow(hero: Hero, itemId: String, index: ContentIndex): Long {
        val method = "stashSell"
        val item = hero.overflow.firstOrNull { it.id == itemId } ?: throw CharacterExceptions.funExceptionItemNotFound(method, itemId)
        val template = index.template(item.template) ?: throw CharacterExceptions.funExceptionEquipmentNotFound(method, item.template)
        if (item.locked) throw CharacterExceptions.funExceptionItemLocked(method, template.code)
        val gold = SellPrice.of(index, template, item, index.sheetOf(hero).stats)
        hero.overflow.remove(item)
        hero.gain(gold)
        hero.count(Counter.ITEMS_SOLD)
        return gold
    }

    /** Докупить пачку мест за золото: цена растёт с каждой, на потолке докупать нечего. */
    fun expand(hero: Hero, index: ContentIndex): Long {
        val method = "stashExpand"
        val price = index.rules.stash.price(hero.stashSlots)
        if (price <= 0) throw CharacterExceptions.funExceptionStashMax(method, index.rules.stash.maxSlots.toString())
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        hero.pay(price)
        hero.stashSlots += 1
        return price
    }
}
