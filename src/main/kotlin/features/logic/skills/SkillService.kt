package features.logic.skills

import application.enums.EnumEquipmentType
import base.exception.model.CharacterExceptions
import base.exception.model.SkillExceptions
import config.MongoFactory.transactionExecute
import features.data.character.Character
import features.data.character.CharacterRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Умения героя (с 0.69.0): изучить уровень книгой, поставить умение в слот и задать условие,
 * задать условие глотка фляги, обменять книги. Всё - одной записью персонажа.
 */
class SkillService : KoinComponent {
    private val characters: CharacterRepository by inject()

    /**
     * Книга умения своего класса поднимает его на уровень; требования нового уровня - к уровню героя
     * и характеристикам класса. Впервые изученное умение само встаёт в первый свободный открытый слот.
     */
    suspend fun learn(characterId: String, code: String): HeroSkills {
        val method = "learnSkill"
        val character = characters.requireCharacter(characterId, method)
        val skill = own(character, code, method)
        val level = character.skills.level(code) + 1
        if (level > SkillRules.MAX_LEVEL) throw SkillExceptions.funExceptionMaxLevel(method, code)
        val unmet = SkillRules.unmet(skill, level, character.level.toInt(), characters.calculateStats(character).stats)
        if (unmet.isNotEmpty()) throw SkillExceptions.funExceptionRequirement(method, code, level.toString(), unmet.joinToString())
        spendBook(character, code, method)
        val learned = character.skills.learned + (code to level)
        character.skills = place(character.skills.copy(learned = learned), skill, character.level.toInt(), level == 1)
        return save(character, method)
    }

    /**
     * Слот [index] вида [kind] получает умение [code] (null - опустеет) и условие [condition] (у активного;
     * null - условие умения). Умение, что стояло в другом слоте, оттуда уходит.
     */
    suspend fun slot(characterId: String, kind: SkillKind, index: Int, code: String?, condition: SlotCondition?): HeroSkills {
        val method = "slotSkill"
        val character = characters.requireCharacter(characterId, method)
        val opens = SkillRules.slotLevel(kind, index) ?: throw SkillExceptions.funExceptionSlot(method, index.toString())
        if (character.level < opens) throw SkillExceptions.funExceptionSlotLocked(method, opens.toString())
        val skills = character.skills
        val skill = code?.let { own(character, it, method) }
        if (skill != null && skills.level(skill.code) <= 0) throw SkillExceptions.funExceptionNotLearned(method, skill.code)
        if (skill != null && skill.kind != kind) throw SkillExceptions.funExceptionWrongKind(method, skill.code)
        if (condition != null && condition.flaskOnly) throw SkillExceptions.funExceptionCondition(method, condition.name)
        character.skills = when (kind) {
            SkillKind.ACTIVE -> skills.copy(active = skills.active.filled(index).also { slots ->
                if (skill != null) slots.replaceAll { if (it?.skill == skill.code) null else it }
                slots[index] = skill?.let { ActiveSlot(it.code, condition ?: it.condition) }
            })
            SkillKind.PASSIVE -> skills.copy(passive = skills.passive.filled(index).also { slots ->
                if (skill != null) slots.replaceAll { if (it == skill.code) null else it }
                slots[index] = skill?.code
            })
        }
        return save(character, method)
    }

    /** Условие глотка фляги на месте пояса [index]; null - условие её вида. */
    suspend fun flask(characterId: String, index: Int, condition: SlotCondition?): HeroSkills {
        val method = "flaskCondition"
        val character = characters.requireCharacter(characterId, method)
        if (index !in EnumEquipmentType.FLASKS.indices) throw SkillExceptions.funExceptionSlot(method, index.toString())
        character.skills = character.skills.copy(flasks = character.skills.flasks.filled(index).also { it[index] = condition })
        return save(character, method)
    }

    /**
     * Обмен: [ExchangeRule.books] любых книг из сумки и золото за уровень героя - на книгу [code]
     * своего класса, открытую не выше уровня героя.
     */
    suspend fun exchange(characterId: String, books: List<String>, code: String): HeroSkills {
        val method = "exchangeBooks"
        val character = characters.requireCharacter(characterId, method)
        val rule = SkillContent.book.rules.exchange
        if (books.size != rule.books || books.any { SkillContent.skills[it] == null }) throw SkillExceptions.funExceptionExchange(method, rule.books.toString())
        val skill = own(character, code, method)
        if (skill.unlock > character.level) throw SkillExceptions.funExceptionRequirement(method, code, "1", "level ${skill.unlock}")
        val price = rule.goldPerLevel * character.level
        if (character.money < price) throw CharacterExceptions.funExceptionGold(method, price.toString())
        books.forEach { spendBook(character, it, method) }
        character.money -= price
        val id = SkillRules.bookId(code)
        character.bag[id] = (character.bag[id] ?: 0L) + 1
        return save(character, method)
    }

    /** Умение класса героя по коду: чужое и неизвестное - отказ. */
    private fun own(character: Character, code: String, method: String): SkillDefinition {
        val skill = SkillContent.skills[code] ?: throw SkillExceptions.funExceptionNotFound(method, code)
        if (skill.heroClass != characters.requireClass(character).code) throw SkillExceptions.funExceptionOtherClass(method, code)
        return skill
    }

    /** Книга уходит из сумки в памяти; запишет её [save] вместе с умениями. */
    private fun spendBook(character: Character, code: String, method: String) {
        val id = SkillRules.bookId(code)
        val owned = character.bag[id] ?: 0L
        if (owned < 1) throw SkillExceptions.funExceptionNoBook(method, code)
        if (owned == 1L) character.bag.remove(id) else character.bag[id] = owned - 1
    }

    /** Впервые изученное умение - в первый свободный открытый слот своего вида, если такой есть. */
    private fun place(skills: HeroSkills, skill: SkillDefinition, heroLevel: Int, first: Boolean): HeroSkills {
        if (!first) return skills
        return when (skill.kind) {
            SkillKind.ACTIVE -> {
                val slots = skills.active.filled(SkillRules.activeSlots(heroLevel) - 1)
                val free = slots.indices.firstOrNull { it < SkillRules.activeSlots(heroLevel) && slots[it] == null } ?: return skills
                skills.copy(active = slots.also { it[free] = ActiveSlot(skill.code, skill.condition) })
            }
            SkillKind.PASSIVE -> {
                val slots = skills.passive.filled(SkillRules.passiveSlots(heroLevel) - 1)
                val free = slots.indices.firstOrNull { it < SkillRules.passiveSlots(heroLevel) && slots[it] == null } ?: return skills
                skills.copy(passive = slots.also { it[free] = skill.code })
            }
        }
    }

    private suspend fun save(character: Character, method: String): HeroSkills {
        transactionExecute(method) { session -> characters.update(character, session) }
        return character.skills
    }

    /** Список, дотянутый null до индекса [index] включительно, - изменяемая копия. */
    private fun <T> List<T?>.filled(index: Int): MutableList<T?> =
        toMutableList().also { list -> while (list.size <= index) list.add(null) }
}
