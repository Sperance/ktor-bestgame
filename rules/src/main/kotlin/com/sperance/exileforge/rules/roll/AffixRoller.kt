package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.table.Weighted

/**
 * Ролл модификаторов копии - как в PoE: при создании из шаблона сразу тянутся префиксы и суффиксы
 * из таблиц шаблона, внутри каждого - тир по уровню и доля ролла. Здесь же операции сфер: доролл
 * одного аффикса, перекат долей, порча, влияние. Чистые функции над [ContentIndex] и [Dice].
 */
class AffixRoller(private val index: ContentIndex) {

    fun definition(roll: Roll): ModifierDef? = index.modifier(roll.code)
    fun definitions(rolls: Collection<Roll>): List<ModifierDef> = rolls.mapNotNull { index.modifier(it.code) }
    fun isAffix(roll: Roll): Boolean = definition(roll)?.affix == true
    fun isEssence(roll: Roll): Boolean = definition(roll)?.source == Source.ESSENCE
    fun isCrafted(roll: Roll): Boolean = definition(roll)?.crafted == true
    fun affixes(rolls: Collection<Roll>): List<Roll> = rolls.filter(::isAffix)
    /** Постоянные строки копии: всё, что не аффикс и не строка эссенции. */
    fun permanent(rolls: Collection<Roll>): List<Roll> = rolls.filterNot { isAffix(it) || isEssence(it) }
    fun fractured(rolls: Collection<Roll>): List<Roll> = rolls.filter { it.fractured }

    /** Новая копия: закреплённые строки шаблона и аффиксы под редкость на уровне предмета [level]. */
    fun roll(template: ItemTemplate, rarity: Rarity, dice: Dice, influence: Influence? = null, level: Int = template.level): List<Roll> =
        rollPermanent(template, dice) + rollAffixes(template, rarity, dice, influence, level = level)

    /** Закреплённые описания шаблона: имплиситы, зачарования, порча, строки уникалки. */
    fun rollPermanent(template: ItemTemplate, dice: Dice): List<Roll> =
        template.fixedCodes.mapNotNull { index.modifier(it) }.filter { it.source.permanent }.mapNotNull { roll(it, template.level, dice) }

    /**
     * Случайные префиксы и суффиксы на места, которые оставила редкость: взвешенно и без повторов
     * группы; [kept] - аффиксы, что остаются на копии и занимают свои места и группы; [below] - на
     * сколько потолок редкости ниже обычного (сфера алхимии не даёт полного набора); [level] - уровень предмета.
     */
    fun rollAffixes(
        template: ItemTemplate, rarity: Rarity, dice: Dice, influence: Influence? = null, kept: Collection<Roll> = emptyList(), below: Int = 0,
        level: Int = template.level, side: Source? = null,
    ): List<Roll> {
        val keptDefs = definitions(kept)
        val (prefixes, suffixes) = freeSlots(rarity, keptDefs, template.slot)
        val limits = index.limits(rarity, template.slot)
        val limit = (dice.between(limits.floor, (limits.ceiling - below).coerceAtLeast(limits.floor)) - kept.size).coerceAtLeast(0)
        return pickAffixes(affixPool(template, influence).onSide(side), prefixes, suffixes, keptDefs.map { it.groupKey }, limit, dice).mapNotNull { roll(it, level, dice) }
    }

    /**
     * Доводит аффиксы копии до правил её редкости: лишние сверх мест снимаются, недостающие до дна
     * дороллены; закреплённые не трогаются. Null - копия уже в порядке.
     */
    fun normalize(template: ItemTemplate, rarity: Rarity, rolls: List<Roll>, dice: Dice, influence: Influence? = null, level: Int = template.level): List<Roll>? {
        if (rarity.fixed) return null
        val result = rolls.toMutableList()
        val limits = index.limits(rarity, template.slot)
        listOf(Source.PREFIX to limits.prefixes, Source.SUFFIX to limits.suffixes).forEach { (source, cap) ->
            val removable = result.filter { !it.fractured && definition(it)?.source == source }
            val over = result.count { definition(it)?.source == source } - cap
            if (over > 0) result.removeAll(removable.takeLast(over).toSet())
        }
        while (result.count(::isAffix) < limits.floor) result += rollExtraAffix(template, rarity, result, dice, influence, level) ?: break
        return result.takeIf { it != rolls }
    }

    /** Правило «волшебный или редкий предмет не пустой»: копия ниже дна редкости дороллена до него. */
    fun ensureAffixes(template: ItemTemplate, item: ItemInstance, dice: Dice): Boolean {
        val floor = index.limits(item.rarity, template.slot).floor
        if (item.rarity.fixed || floor == 0 || item.rolls.count(::isAffix) >= floor) return false
        item.rolls = normalize(template, item.rarity, item.rolls, dice, item.influence, item.level(template)) ?: return false
        return true
    }

    /** Один аффикс сверх имеющихся на свободное место; null - мест нет или таблица исчерпана. */
    fun rollExtraAffix(template: ItemTemplate, rarity: Rarity, current: Collection<Roll>, dice: Dice, influence: Influence? = null, level: Int = template.level,
                       side: Source? = null): Roll? =
        rollExtraFrom(affixPool(template, influence).onSide(side), template, rarity, current, dice, level)

    /** Пул одной стороны аффиксов - знамение (1.35.0); null - обе. */
    private fun List<Weighted<ModifierDef>>.onSide(side: Source?): List<Weighted<ModifierDef>> = if (side == null) this else filter { it.value.source == side }

    /** Один аффикс из [pool] сверх имеющихся - в пределах потолка, мест префиксов и суффиксов и групп; null - нельзя. */
    fun rollExtraFrom(pool: List<Weighted<ModifierDef>>, template: ItemTemplate, rarity: Rarity, current: Collection<Roll>, dice: Dice, level: Int = template.level): Roll? {
        if (current.count(::isAffix) >= index.limits(rarity, template.slot).ceiling) return null
        return rollOne(pool, template, rarity, current, dice, level)
    }

    /** Один модификатор таблицы влияния - то, что делает сфера влияния. */
    fun rollInfluenced(template: ItemTemplate, rarity: Rarity, current: Collection<Roll>, influence: Influence, dice: Dice, level: Int = template.level): Roll? =
        rollOne(index.affixPool(influenceTags(influence, template.slot)), template, rarity, current, dice, level)

    /** Модификатор влияния наверняка: на свободное место, а без него - вместо случайного незакреплённого аффикса. */
    fun forceInfluenced(template: ItemTemplate, rarity: Rarity, rolls: MutableList<Roll>, influence: Influence, dice: Dice, level: Int = template.level): Boolean {
        rollInfluenced(template, rarity, rolls, influence, dice, level)?.let { rolls += it; return true }
        dice.shuffled(rolls.filter { isAffix(it) && !it.fractured }).forEach { old ->
            rollInfluenced(template, rarity, rolls - old, influence, dice, level)?.let { rolls[rolls.indexOf(old)] = it; return true }
        }
        return false
    }

    private fun rollOne(pool: List<Weighted<ModifierDef>>, template: ItemTemplate, rarity: Rarity, current: Collection<Roll>, dice: Dice, level: Int): Roll? {
        val defs = definitions(current)
        val (prefixes, suffixes) = freeSlots(rarity, defs, template.slot)
        return pickAffixes(pool, minOf(prefixes, 1), minOf(suffixes, 1), defs.map { it.groupKey }, 1, dice).firstOrNull()?.let { roll(it, level, dice) }
    }

    /** Взвешенный выбор аффиксов на свободные места без повторов группы: места двух видов тянутся из одного мешка. */
    fun pickAffixes(pool: Collection<Weighted<ModifierDef>>, prefixes: Int, suffixes: Int, taken: Collection<String>, limit: Int, dice: Dice): List<ModifierDef> {
        val groups = taken.toHashSet()
        var freePrefixes = prefixes
        var freeSuffixes = suffixes
        val candidates = pool.filterTo(ArrayList()) { (def) -> def.groupKey !in groups && def.affix }
        val picked = mutableListOf<ModifierDef>()
        while (picked.size < limit && candidates.isNotEmpty()) {
            val live = candidates.filter { (def) -> if (def.source == Source.PREFIX) freePrefixes > 0 else freeSuffixes > 0 }
            val next = Tables.draw(live, dice) ?: break
            picked += next
            if (next.source == Source.PREFIX) freePrefixes-- else freeSuffixes--
            candidates.removeAll { (def) -> def.groupKey == next.groupKey || (def.source == Source.PREFIX && freePrefixes == 0) || (def.source == Source.SUFFIX && freeSuffixes == 0) }
        }
        return picked
    }

    fun freeSlots(rarity: Rarity, current: Collection<ModifierDef>, slot: Slot): Pair<Int, Int> {
        val limits = index.limits(rarity, slot)
        return (limits.prefixes - current.count { it.source == Source.PREFIX }).coerceAtLeast(0) to
            (limits.suffixes - current.count { it.source == Source.SUFFIX }).coerceAtLeast(0)
    }

    /** Перекат долей ролла с теми же описаниями и тирами - Divine Orb; закреплённые не трогаются. */
    fun rerollShares(rolls: Collection<Roll>, dice: Dice, filter: (ModifierDef) -> Boolean = { true }): List<Roll> = rolls.map { roll ->
        if (roll.fractured) return@map roll
        val def = definition(roll) ?: return@map roll
        if (!filter(def) || def.tier(roll.tier) == null) roll else roll.copy(share = dice.share(), scale = null)
    }

    /** Один модификатор таблиц [tags] по весам - порча, ручная работа. */
    fun rollFrom(tags: List<String>, level: Int, dice: Dice): Roll? = Tables.draw(index.modifierPool(tags), dice)?.let { roll(it, level, dice) }

    /** Ролл описания: тир по уровню, доля внутри него; null у описания без тиров. */
    fun roll(def: ModifierDef, level: Int, dice: Dice): Roll? {
        val (number, _) = index.rollTier(def.code, level, dice) ?: return null
        return Roll(def.code, number, dice.share())
    }

    fun rollCode(code: String, level: Int, dice: Dice): Roll? = index.modifier(code)?.let { roll(it, level, dice) }

    /** Тот же модификатор на тир выше: лучший остаётся собой, доля перебрасывается. */
    fun raiseTier(roll: Roll, dice: Dice): Roll? {
        if (roll.fractured || roll.tier <= 1) return null
        definition(roll)?.tier(roll.tier - 1) ?: return null
        return roll.copy(tier = roll.tier - 1, share = dice.share())
    }

    /** Описание на тире, взятом долей [tierShare] лестницы: 0 - худший, 1 - лучший, уровень не спрашивается. */
    fun rollShare(code: String, tierShare: Double, dice: Dice): Roll? {
        val def = index.modifier(code) ?: return null
        val count = def.tiers.size.takeIf { it > 0 } ?: return null
        val number = (count - Math.round(tierShare.coerceIn(0.0, 1.0) * (count - 1)).toInt()).coerceIn(1, count)
        return Roll(def.code, number, dice.share())
    }

    /** Таблицы аффиксов копии: таблицы шаблона и, под влиянием, таблицы влияния. */
    fun affixPool(template: ItemTemplate, influence: Influence? = null): List<Weighted<ModifierDef>> =
        index.affixPool(if (influence == null) template.tables else template.tables + influenceTags(influence, template.slot))

    companion object {
        fun influenceTags(influence: Influence, slot: Slot): List<String> = listOf("influence:${influence.name}:${slot.tag}", "influence:${influence.name}")
        fun corruptionTag(slot: Slot): String = "corruption:${slot.tag}"
    }
}
