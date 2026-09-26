package features.logic.skills

import application.enums.IntEnumStat
import extensions.toStableObjectId
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.random.Random

/** Слот активного умения: что в нём и когда автобой его применяет. */
@Serializable
data class ActiveSlot(val skill: String, val condition: SlotCondition)

/**
 * Умения героя (с 0.69.0): изученные уровни по кодам; слоты по индексу - три активных и два
 * пассивных, null - пусто; условия глотков трёх мест пояса, null - условие вида фляги.
 */
@Serializable
data class HeroSkills(
    val learned: Map<String, Int> = emptyMap(),
    val active: List<ActiveSlot?> = emptyList(),
    val passive: List<String?> = emptyList(),
    val flasks: List<SlotCondition?> = emptyList(),
) {
    fun level(code: String): Int = learned[code] ?: 0
}

/** Чего не хватает герою для уровня умения: уровня героя или характеристики. */
data class SkillNeed(val heroLevel: Int, val attributes: Map<IntEnumStat, Int>)

/**
 * Правила книги умений - чистые функции над книгой [SkillContent.book]: требования уровней,
 * слоты по уровню героя, стартовый набор, книги с монстров.
 */
object SkillRules {
    /** Потолок уровня умения книгами; уровни от экипировки ложатся сверх него. */
    const val MAX_LEVEL = 20

    /** Уровень героя, к которому тянутся требования двадцатого уровня умения. */
    const val MAX_HERO_LEVEL = 70

    const val BOOK_PREFIX = "BOOK_"

    fun book(code: String): String = BOOK_PREFIX + code

    /** Id книги в сумке - как у любого предмета, стабильный от кода. */
    fun bookId(code: String): String = book(code).toStableObjectId()

    private val rules get() = SkillContent.book.rules

    /** Уровень героя, с которого учится [level]-й уровень умения: от открытия ровно до 70-го к двадцатому. */
    fun heroLevel(skill: SkillDefinition, level: Int): Int =
        ceil(skill.unlock + (MAX_HERO_LEVEL - skill.unlock) * (level - 1) / (MAX_LEVEL - 1.0) - 1e-9).toInt()

    /** Сколько каждой характеристики класса просит [level]-й уровень умения. */
    fun need(skill: SkillDefinition, level: Int): SkillNeed {
        val heroLevel = heroLevel(skill, level)
        val heroClass = SkillContent.classes.getValue(skill.heroClass)
        val (factor, plus) = when (heroClass.attributes.size) {
            1 -> rules.attributes.single
            2 -> rules.attributes.dual
            else -> rules.attributes.triple
        }
        val amount = ceil(factor * heroLevel + plus - 1e-9).toInt()
        return SkillNeed(heroLevel, heroClass.attributes.associateWith { amount })
    }

    /** Что из требований [level]-го уровня не выполнено при уровне [heroLevel] и листе [stats]: пусто - можно учить. */
    fun unmet(skill: SkillDefinition, level: Int, heroLevel: Int, stats: Map<IntEnumStat, Double>): List<String> {
        val need = need(skill, level)
        return listOfNotNull("level ${need.heroLevel}".takeIf { heroLevel < need.heroLevel }) +
            need.attributes.filter { (stat, amount) -> (stats[stat] ?: 0.0) < amount }.map { (stat, amount) -> "${(stat as Enum<*>).name} $amount" }
    }

    /** Сколько слотов открыто на уровне героя. */
    fun activeSlots(heroLevel: Int): Int = rules.activeSlots.count { it <= heroLevel }
    fun passiveSlots(heroLevel: Int): Int = rules.passiveSlots.count { it <= heroLevel }

    /** Уровень, на котором открывается слот; null - такого слота нет. */
    fun slotLevel(kind: SkillKind, index: Int): Int? = (if (kind == SkillKind.ACTIVE) rules.activeSlots else rules.passiveSlots).getOrNull(index)

    /** Мана класса на [level]-м уровне без интеллекта: база класса и прирост за каждый уровень. */
    fun mana(heroClass: String, level: Int): Double = (SkillContent.classes[heroClass]?.mana ?: 0.0) + rules.manaPerLevel * level

    /**
     * Стартовый набор нового героя: первое активное и первое пассивное умение класса на первом
     * уровне и сразу в слотах.
     */
    fun starter(heroClass: String): HeroSkills {
        val own = SkillContent.ofClass(heroClass)
        val active = own.first { it.kind == SkillKind.ACTIVE && it.unlock == 1 }
        val passive = own.first { it.kind == SkillKind.PASSIVE && it.unlock == 1 }
        return HeroSkills(
            learned = mapOf(active.code to 1, passive.code to 1),
            active = listOf(ActiveSlot(active.code, active.condition)),
            passive = listOf(passive.code),
        )
    }

    /**
     * Книга с монстра: с шансом [chance] - умение, открытое не выше уровня зоны + [BookRule.reach];
     * своего класса - с долей [ownShare], иначе любого. Возвращает код книги или null.
     */
    fun dropBook(heroClass: String, zoneLevel: Int, chance: Double, ownShare: Double, random: Random): String? {
        if (random.nextDouble() >= chance) return null
        val open = SkillContent.book.skills.filter { it.unlock <= zoneLevel + rules.books.reach }
        val own = open.filter { it.heroClass == heroClass }
        val from = if (own.isNotEmpty() && random.nextDouble() < ownShare) own else open
        return from.randomOrNull(random)?.book
    }
}
