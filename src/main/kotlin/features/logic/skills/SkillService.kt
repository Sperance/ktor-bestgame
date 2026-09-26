package features.logic.skills

import base.exception.model.CharacterExceptions
import base.exception.model.SkillExceptions
import com.sperance.exileforge.rules.content.ActiveSlot
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroSkills
import com.sperance.exileforge.rules.content.SkillDefinition
import com.sperance.exileforge.rules.content.SkillKind
import com.sperance.exileforge.rules.content.SkillRules
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.SlotCondition
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.hero.sheetOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Умения героя: книга учит уровень, слоты с условиями, условия глотков фляг, обмен книг. Всё - одной записью героя. */
class SkillService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val rules: SkillRules get() = index.skillRules

    /** Книга своего класса поднимает умение на уровень; впервые изученное само встаёт в свободный открытый слот. */
    suspend fun learn(heroId: String, code: String): HeroSkills {
        val method = "learnSkill"
        val hero = heroes.requireHero(heroId, method)
        val skill = own(hero, code, method)
        val level = hero.skills.level(code) + 1
        if (level > SkillRules.MAX_LEVEL) throw SkillExceptions.funExceptionMaxLevel(method, code)
        val unmet = rules.unmet(skill, level, hero.level, index.sheetOf(hero).stats)
        if (unmet.isNotEmpty()) throw SkillExceptions.funExceptionRequirement(method, code, level.toString(), unmet.joinToString())
        spendBook(hero, code, method)
        hero.skills = place(hero.skills.copy(learned = hero.skills.learned + (code to level)), skill, hero.level, level == 1)
        return heroes.save(hero, method).skills
    }

    /** Слот [index] вида [kind] получает умение [code] (null - опустеет) и условие; умение из другого слота оттуда уходит. */
    suspend fun slot(heroId: String, kind: SkillKind, index: Int, code: String?, condition: SlotCondition?): HeroSkills {
        val method = "slotSkill"
        val hero = heroes.requireHero(heroId, method)
        val opens = rules.slotLevel(kind, index) ?: throw SkillExceptions.funExceptionSlot(method, index.toString())
        if (hero.level < opens) throw SkillExceptions.funExceptionSlotLocked(method, opens.toString())
        val skills = hero.skills
        val skill = code?.let { own(hero, it, method) }
        if (skill != null && skills.level(skill.code) <= 0) throw SkillExceptions.funExceptionNotLearned(method, skill.code)
        if (skill != null && skill.kind != kind) throw SkillExceptions.funExceptionWrongKind(method, skill.code)
        if (condition != null && condition.flaskOnly) throw SkillExceptions.funExceptionCondition(method, condition.name)
        hero.skills = when (kind) {
            SkillKind.ACTIVE -> skills.copy(active = skills.active.filled(index).also { slots ->
                if (skill != null) slots.replaceAll { if (it?.skill == skill.code) null else it }
                slots[index] = skill?.let { ActiveSlot(it.code, condition ?: it.condition) }
            })
            SkillKind.PASSIVE -> skills.copy(passive = skills.passive.filled(index).also { slots ->
                if (skill != null) slots.replaceAll { if (it == skill.code) null else it }
                slots[index] = skill?.code
            })
        }
        return heroes.save(hero, method).skills
    }

    /** Условие глотка фляги на месте пояса [index]; null - условие её вида. */
    suspend fun flask(heroId: String, index: Int, condition: SlotCondition?): HeroSkills {
        val method = "flaskCondition"
        val hero = heroes.requireHero(heroId, method)
        if (index !in Slot.FLASKS.indices) throw SkillExceptions.funExceptionSlot(method, index.toString())
        hero.skills = hero.skills.copy(flasks = hero.skills.flasks.filled(index).also { it[index] = condition })
        return heroes.save(hero, method).skills
    }

    /** Обмен: столько книг любых умений и золото за уровень героя - на книгу [code] своего класса, открытую не выше уровня. */
    suspend fun exchange(heroId: String, books: List<String>, code: String): HeroSkills {
        val method = "exchangeBooks"
        val hero = heroes.requireHero(heroId, method)
        val rule = index.skills.rules.exchange
        if (books.size != rule.books || books.any { index.skills.byCode[it] == null }) throw SkillExceptions.funExceptionExchange(method, rule.books.toString())
        val skill = own(hero, code, method)
        if (skill.unlock > hero.level) throw SkillExceptions.funExceptionRequirement(method, code, "1", "level ${skill.unlock}")
        val price = rule.goldPerLevel * hero.level
        if (hero.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        books.forEach { spendBook(hero, it, method) }
        hero.money -= price
        hero.earn(SkillRules.book(code), 1, index.rules.maxStack)
        return heroes.save(hero, method).skills
    }

    private fun own(hero: Hero, code: String, method: String): SkillDefinition {
        val skill = index.skills.byCode[code] ?: throw SkillExceptions.funExceptionNotFound(method, code)
        if (skill.heroClass != hero.heroClass) throw SkillExceptions.funExceptionOtherClass(method, code)
        return skill
    }

    private fun spendBook(hero: Hero, code: String, method: String) {
        if ((hero.bag[SkillRules.book(code)] ?: 0L) < 1) throw SkillExceptions.funExceptionNoBook(method, code)
        hero.spend(SkillRules.book(code), 1, method)
    }

    private fun place(skills: HeroSkills, skill: SkillDefinition, heroLevel: Int, first: Boolean): HeroSkills {
        if (!first) return skills
        return when (skill.kind) {
            SkillKind.ACTIVE -> {
                val open = rules.activeSlots(heroLevel)
                val slots = skills.active.filled(open - 1)
                val free = slots.indices.firstOrNull { it < open && slots[it] == null } ?: return skills
                skills.copy(active = slots.also { it[free] = ActiveSlot(skill.code, skill.condition) })
            }
            SkillKind.PASSIVE -> {
                val open = rules.passiveSlots(heroLevel)
                val slots = skills.passive.filled(open - 1)
                val free = slots.indices.firstOrNull { it < open && slots[it] == null } ?: return skills
                skills.copy(passive = slots.also { it[free] = skill.code })
            }
        }
    }

    private fun <T> List<T?>.filled(index: Int): MutableList<T?> = toMutableList().also { list -> while (list.size <= index) list.add(null) }
}
