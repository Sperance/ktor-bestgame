package ru.descend.exileforge.features.logic.guild
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.GuildEffect
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildRules
import com.sperance.exileforge.rules.content.Item
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.exception.model.GuildExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.features.data.guild.GuildContribution
import ru.descend.exileforge.features.data.guild.GuildMine
import ru.descend.exileforge.features.data.hero.HeroRepository

/** Вклад в гильдию и её древо бонусов (1.74.0): казна, опыт, ранги участников, узлы древа и их сброс. */
class GuildTreeService(
    private val heroes: HeroRepository,
    private val content: ContentStore,
    private val access: GuildAccess,
) {
    private val index: ContentIndex get() = content.index
    private val rules: GuildRules get() = index.guilds

    // ==================== ВКЛАД ====================

    /**
     * Вклад золотом или сферами: всё уходит в казну, золотая стоимость - в опыт гильдии, личный вклад
     * (ранг), неделю и суточный потолок; за стоимость герой получает знаки гильдии. Материалы ремёсел (1.74.0)
     * идут тем же счётом, но в казне не лежат - гильдия их расходует.
     */
    suspend fun contribute(heroId: String, item: String, amount: Long): GuildContribution {
        val method = "guildContribute"
        if (amount <= 0) throw GuildExceptions.funExceptionAmount(method, amount.toString())
        val change = access.acting(heroId, method)
        val hero = change.actor
        val guild = change.guild
        val me = change.record(heroId)
        val gold = item.equals(GOLD, ignoreCase = true)
        val value = if (gold) {
            amount
        } else {
            val stock = index.item(item)?.takeIf { it.category in DONATED && it.price > 0 } ?: throw GuildExceptions.funExceptionNotOrb(method, item)
            Math.multiplyExact(stock.price, amount)
        }
        val today = GuildClock.day(change.now)
        if (me.day != today) {
            me.day = today
            me.dayContribution = 0
        }
        val limit = (rules.dailyLimit(hero.level) * (1 + change.effect(GuildEffect.DAILY_LIMIT) / 100)).toLong()
        val left = (limit - me.dayContribution).coerceAtLeast(0)
        if (value > left) throw GuildExceptions.funExceptionDailyLimit(method, left.toString())
        if (gold) {
            if (hero.money < amount) throw CharacterExceptions.funExceptionGold(method, amount.toString())
            hero.pay(amount)
            guild.treasuryGold += amount
        } else {
            hero.spend(item, amount, method)
            if (index.item(item)?.category == Item.CURRENCY) guild.treasuryOrbs.merge(item, amount, Long::plus)
        }
        val rankBefore = rules.rankIndex(me.contribution)
        me.contribution += value
        me.dayContribution += value
        val week = GuildClock.week(change.now)
        if (me.week != week) {
            me.week = week
            me.weekContribution = 0
        }
        me.weekContribution += value
        change.touch(hero)
        change.log(GuildLogKind.CONTRIBUTED, hero.name, "$amount ${if (gold) GOLD else item}")
        val rankAfter = rules.rankIndex(me.contribution)
        if (rankAfter > rankBefore) change.log(GuildLogKind.RANK_UP, hero.name, rules.ranks[rankAfter].code)
        val grown = change.grow((value * (1 + change.effect(GuildEffect.GROWTH) / 100)).toLong(), hero.name)
        change.sync(hero)
        access.commit(change, method, grown)
        val cards = heroes.cards(guild.members.map { it.heroId })
        return GuildContribution(access.view(change, me, cards), access.member(me, cards[heroId], change.now), hero.money)
    }

    // ==================== ДРЕВО (1.74.0) ====================

    /** Глава берёт ранг узла: очко за уровень гильдии, второй ряд - с правила очков в ветви. Строки - у каждого участника. */
    suspend fun takeNode(heroId: String, node: String): GuildMine {
        val method = "guildTreeTake"
        val change = access.acting(heroId, method, leader = true)
        val tree = rules.tree
        if (!tree.canTake(change.guild.tree, change.guild.level, node)) throw GuildExceptions.funExceptionNode(method, node)
        change.guild.tree.merge(node, 1, Int::plus)
        change.log(GuildLogKind.TREE_NODE, change.actor.name, node)
        return treeChanged(change, method)
    }

    /** Глава сбрасывает древо: очки возвращаются; бесплатно раз в правило дней. */
    suspend fun resetTree(heroId: String): GuildMine {
        val method = "guildTreeReset"
        val change = access.acting(heroId, method, leader = true)
        val at = change.guild.treeResetAt + rules.tree.respecDays * GuildClock.DAY
        if (change.now < at) throw GuildExceptions.funExceptionRespec(method, at.toString())
        change.guild.tree.clear()
        change.guild.treeResetAt = change.now
        change.log(GuildLogKind.TREE_RESET, change.actor.name, "")
        return treeChanged(change, method)
    }

    /** Древо поменялось: строки в копии гильдии у каждого участника - одной записью. */
    private suspend fun treeChanged(change: GuildChange, method: String): GuildMine {
        val guild = change.guild
        val bonuses = rules.tree.effects(guild.tree)
        change.sync(change.actor)
        transactionExecute("guild $method ${guild._id}") { session ->
            change.write(session)
            heroes.patchGuild(Filters.and(Filters.eq("guild.id", guild._id), Filters.ne("_id", change.actor._id)), Updates.set("guild.bonuses", bonuses), session)
        }
        return access.inside(change, change.actor)
    }

    private companion object {
        const val GOLD = "GOLD"

        /** Что принимает вклад кроме золота (1.74.0): сферы - в казну, материалы ремёсел - в опыт гильдии. */
        val DONATED = setOf(Item.CURRENCY, Item.MATERIAL, "STONE_STOCK", "WOOD_STOCK")
    }
}
