package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/** Вариант работы с выбором: [choice] - что выбрано (код предмета на выходе или [SmithChoice]), [job] - работа под него. */
@Serializable
data class Recipe(val choice: String, val job: Job)

/**
 * Работы с выбором (1.43.0): одна работа вместо десятков одинаковых. Сгущение эссенций ([JobKind.CONDENSE])
 * выбирает вид и ступень - вход, уровень и опыт берутся из правила `condense` эссенций; переписывание книги
 * ([JobKind.BOOK]) - умение класса героя, открытое не позже потолка работы; перегонка ([JobKind.REFINE], 1.44.0) -
 * ступень цепочки добычи своей профессии. Выбранный вариант - обычная работа [JobKind.ITEM], и циклы, расход и выход
 * считает прежний [com.sperance.exileforge.rules.roll.Work]. Вещь кузнеца ([JobKind.EQUIPMENT], 1.44.0) остаётся
 * вещью: выбор - группа слотов и атрибут ([SmithChoice]), самоцвет атрибута добавляется ко входу.
 */
class JobRecipes(private val index: ContentIndex) {

    /** Все варианты [job] для героя класса [heroClass]; у работы без выбора их нет. */
    fun options(job: Job, heroClass: String): List<Recipe> = when (job.kind) {
        JobKind.CONDENSE -> condense(job)
        JobKind.BOOK -> books(job, heroClass)
        JobKind.REFINE -> refine(job)
        JobKind.EQUIPMENT -> SmithChoice.entries.map { choice -> Recipe(choice.name, job.copy(inputs = job.inputs + choice.gems.map { JobInput(it, 1) })) }
        else -> emptyList()
    }

    /** Работа, которую надо делать: выбранный вариант или сама [job], если выбора у неё нет; null - выбор не подходит. */
    fun resolve(job: Job, choice: String, heroClass: String): Job? = if (job.kind.chosen) {
        options(job, heroClass).firstOrNull { it.choice == choice }?.job
    } else {
        job.takeIf { choice.isEmpty() }
    }

    private fun condense(job: Job): List<Recipe> {
        val book = index.essences
        return book.kinds.flatMap { kind ->
            (2..book.tiers.size).map { tier ->
                val output = EssenceBook.code(kind.code, tier, special = false)
                Recipe(
                    output,
                    job.copy(
                        kind = JobKind.ITEM,
                        output = output,
                        level = book.condense.levels[tier - 2],
                        experience = job.experience + job.step * (tier - 2),
                        inputs = listOf(JobInput(EssenceBook.code(kind.code, tier - 1, special = false), book.condense.inputs.toLong())),
                    ),
                )
            }
        }
    }

    /** Ступени цепочек своей профессии по порядку уровней: [Job.ratio] низшей - одна следующая, не раньше уровня её работы. */
    private fun refine(job: Job): List<Recipe> {
        val profession = index.professions.jobs[job.code]?.first ?: return emptyList()
        return profession.jobs.filter { it.chain.isNotEmpty() && it.kind == JobKind.ITEM }.groupBy { it.chain }.values.flatMap { chain ->
            chain.sortedBy { it.level }.zipWithNext { low, high ->
                Recipe(
                    high.output,
                    job.copy(
                        kind = JobKind.ITEM,
                        output = high.output,
                        level = maxOf(job.level, high.level),
                        inputs = listOf(JobInput(low.output, job.ratio)),
                    ),
                )
            }
        }
    }

    private fun books(job: Job, heroClass: String): List<Recipe> = index.skills.ofClass(heroClass).filter { it.unlock <= job.band.single() }.map { Recipe(it.book, job.copy(kind = JobKind.ITEM, output = it.book)) }
}

/**
 * Выбор кузнеца (1.44.0): группа слотов и атрибут базы. Атрибут - наибольшее из требований базы и стоит самоцвета
 * своего цвета (рубин - сила, топаз - ловкость, сапфир - интеллект); бижутерия атрибута не спрашивает и берёт по одному
 * самоцвету каждого.
 */
enum class SmithChoice(val slots: Set<Slot>, val attribute: CoreAttribute?, val gems: List<String>) {
    WEAPON_STR(WEAPONS, CoreAttribute.STRENGTH, listOf(RUBY)),
    WEAPON_DEX(WEAPONS, CoreAttribute.DEXTERITY, listOf(TOPAZ)),
    WEAPON_INT(WEAPONS, CoreAttribute.INTELLIGENCE, listOf(SAPPHIRE)),
    ARMOUR_STR(ARMOURS, CoreAttribute.STRENGTH, listOf(RUBY)),
    ARMOUR_DEX(ARMOURS, CoreAttribute.DEXTERITY, listOf(TOPAZ)),
    ARMOUR_INT(ARMOURS, CoreAttribute.INTELLIGENCE, listOf(SAPPHIRE)),
    JEWELLERY(setOf(Slot.RING, Slot.AMULET, Slot.BELT, Slot.COLLAR), null, listOf(RUBY, TOPAZ, SAPPHIRE)),
    ;

    /** Подходит ли база [template] под выбор. */
    fun fits(template: ItemTemplate): Boolean = template.slot in slots && (attribute == null || CoreAttribute.of(template) == attribute)

    companion object {
        fun of(code: String): SmithChoice? = entries.firstOrNull { it.name == code }
    }
}

/** Основной атрибут базы: наибольшее из её требований; без требований - null. */
enum class CoreAttribute {
    STRENGTH,
    DEXTERITY,
    INTELLIGENCE,
    ;

    companion object {
        fun of(template: ItemTemplate): CoreAttribute? = listOf(
            STRENGTH to template.requiredStrength,
            DEXTERITY to template.requiredDexterity,
            INTELLIGENCE to template.requiredIntelligence,
        ).filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }
}

private const val RUBY = "RUBY"
private const val TOPAZ = "TOPAZ"
private const val SAPPHIRE = "SAPPHIRE"
private val WEAPONS = setOf(Slot.WEAPON_1H, Slot.WEAPON_2H, Slot.QUIVER)
private val ARMOURS = setOf(Slot.HELMET, Slot.BODY, Slot.GLOVES, Slot.BOOTS, Slot.SHIELD, Slot.WINGS)
