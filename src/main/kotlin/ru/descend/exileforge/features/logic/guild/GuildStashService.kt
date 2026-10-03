package ru.descend.exileforge.features.logic.guild
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.GuildEffect
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.GuildRules
import ru.descend.exileforge.base.exception.model.AuctionExceptions
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.exception.model.GuildExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.guild.GuildStashEntry
import ru.descend.exileforge.features.data.guild.GuildStashTab
import ru.descend.exileforge.features.data.guild.GuildStashView
import ru.descend.exileforge.features.logic.hero.Stash

/** Хранилище гильдии (1.74.0): вкладки, вложение и взятие вещей, порог ранга на вкладку. */
class GuildStashService(
    private val content: ContentStore,
    private val access: GuildAccess,
) {
    private val index: ContentIndex get() = content.index
    private val rules: GuildRules get() = index.guilds

    suspend fun stash(heroId: String): GuildStashView = stashView(access.reading(heroId, "guildStash"))

    /**
     * Положить в хранилище: вещь из тайника (не надетую) или стопку сумки - во вкладку [tab]; класть могут все.
     * С этого мига вещь - гильдии.
     */
    suspend fun deposit(heroId: String, tab: Int, itemId: String?, code: String?, amount: Long): GuildStashView {
        val method = "guildDeposit"
        val change = access.acting(heroId, method)
        val hero = change.actor
        val guild = change.guild
        if (tab !in 0 until tabCount(change)) throw GuildExceptions.funExceptionStashEntry(method, "tab $tab")
        if (guild.stash.count { it.tab == tab } >= rules.stash.tabSize) throw GuildExceptions.funExceptionStashFull(method, (tab + 1).toString())
        val entry = if (itemId != null) {
            val item = hero.requireItem(itemId, method)
            if (item.equipped || item.socketed) throw AuctionExceptions.funExceptionItemEquipped(method, itemId)
            if (item.locked) throw AuctionExceptions.funExceptionItemLocked(method, itemId)
            hero.items.remove(item)
            GuildStashEntry(tab = tab, item = item.copy(slot = null, socket = null), by = hero.name, at = change.now)
        } else {
            val stack = code?.takeIf { index.item(it) != null } ?: throw CharacterExceptions.funExceptionItemNotFound(method, code.orEmpty())
            if (amount <= 0) throw GuildExceptions.funExceptionAmount(method, amount.toString())
            hero.spend(stack, amount, method)
            GuildStashEntry(tab = tab, code = stack, amount = amount, by = hero.name, at = change.now)
        }
        guild.stash += entry
        change.touch(hero)
        change.log(GuildLogKind.STASH_IN, hero.name, entry.item?.template ?: "${entry.amount} ${entry.code}")
        access.commit(change, method, false)
        return stashView(change)
    }

    /** Взять из хранилища: ранг не ниже порога вкладки, взятий в сутки по правилу (глава и офицеры - без счёта); стопка - одно взятие. */
    suspend fun take(heroId: String, entryId: String): GuildStashView {
        val method = "guildTake"
        val change = access.acting(heroId, method)
        val hero = change.actor
        val guild = change.guild
        val me = change.record(heroId)
        val entry = guild.stash.firstOrNull { it.id == entryId } ?: throw GuildExceptions.funExceptionStashEntry(method, entryId)
        val tab = guild.tabs.getOrNull(entry.tab) ?: GuildStashTab()
        if (me.role == GuildRole.MEMBER && rules.rankIndex(me.contribution) < tab.minRank) throw GuildExceptions.funExceptionTabRank(method, (entry.tab + 1).toString())
        val today = GuildClock.day(change.now)
        if (me.takesDay != today) {
            me.takesDay = today
            me.takes = 0
        }
        val allowed = takesPerDay(change)
        if (me.role == GuildRole.MEMBER && me.takes >= allowed) throw GuildExceptions.funExceptionTakes(method, allowed.toString())
        guild.stash.remove(entry)
        if (me.role == GuildRole.MEMBER) me.takes++
        entry.item?.let { Stash.giveBack(hero, it, index) } ?: hero.earn(entry.code, entry.amount)
        change.touch(hero)
        change.log(GuildLogKind.STASH_OUT, hero.name, entry.item?.template ?: "${entry.amount} ${entry.code}")
        access.commit(change, method, false)
        return stashView(change)
    }

    /** Глава ставит вкладке порог ранга на взятие. */
    suspend fun tabRank(heroId: String, tab: Int, minRank: Int): GuildStashView {
        val method = "guildTabRank"
        val change = access.acting(heroId, method, leader = true)
        if (tab !in 0 until tabCount(change) || minRank !in rules.ranks.indices) throw GuildExceptions.funExceptionStashEntry(method, "tab $tab")
        val tabs = change.guild.tabs
        while (tabs.size <= tab) tabs += GuildStashTab()
        tabs[tab] = GuildStashTab(minRank)
        access.commit(change, method, false)
        return stashView(change)
    }

    private fun tabCount(change: GuildChange): Int = (rules.stash.baseTabs + change.effect(GuildEffect.STASH_TABS).toInt()).coerceIn(1, rules.stash.maxTabs)
    private fun takesPerDay(change: GuildChange): Int = rules.stash.takesPerDay + change.effect(GuildEffect.TAKES).toInt()

    private fun stashView(change: GuildChange): GuildStashView {
        val me = change.record(change.actor._id)
        val count = tabCount(change)
        val left = if (me.role != GuildRole.MEMBER) -1 else takesPerDay(change) - (if (me.takesDay == GuildClock.day(change.now)) me.takes else 0)
        return GuildStashView(
            change.guild.stash.toList(),
            List(count) { change.guild.tabs.getOrNull(it) ?: GuildStashTab() },
            rules.stash.tabSize,
            left.coerceAtLeast(if (left < 0) -1 else 0),
            change.actor.money,
        )
    }
}
