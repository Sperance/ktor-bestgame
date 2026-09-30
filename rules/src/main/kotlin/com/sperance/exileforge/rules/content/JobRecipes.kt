package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/** Вариант работы с выбором: [choice] - что выбрано (код предмета на выходе), [job] - работа под него, вида [JobKind.ITEM]. */
@Serializable
data class Recipe(val choice: String, val job: Job)

/**
 * Работы с выбором (1.43.0): одна работа вместо десятков одинаковых. Сгущение эссенций ([JobKind.CONDENSE])
 * выбирает вид и ступень - вход, уровень и опыт берутся из правила `condense` эссенций; переписывание книги
 * ([JobKind.BOOK]) - умение класса героя, открытое не позже потолка работы. Выбранный вариант - обычная
 * работа [JobKind.ITEM], и циклы, расход и выход считает прежний [com.sperance.exileforge.rules.roll.Work].
 */
class JobRecipes(private val index: ContentIndex) {

    /** Все варианты [job] для героя класса [heroClass]; у работы без выбора их нет. */
    fun options(job: Job, heroClass: String): List<Recipe> = when (job.kind) {
        JobKind.CONDENSE -> condense(job)
        JobKind.BOOK -> books(job, heroClass)
        else -> emptyList()
    }

    /** Работа, которую надо делать: выбранный вариант или сама [job], если выбора у неё нет; null - выбор не подходит. */
    fun resolve(job: Job, choice: String, heroClass: String): Job? =
        if (job.kind.chosen) options(job, heroClass).firstOrNull { it.choice == choice }?.job
        else job.takeIf { choice.isEmpty() }

    private fun condense(job: Job): List<Recipe> {
        val book = index.essences
        return book.kinds.flatMap { kind ->
            (2..book.tiers.size).map { tier ->
                val output = EssenceBook.code(kind.code, tier, special = false)
                Recipe(output, job.copy(
                    kind = JobKind.ITEM, output = output, level = book.condense.levels[tier - 2],
                    experience = job.experience + job.step * (tier - 2),
                    inputs = listOf(JobInput(EssenceBook.code(kind.code, tier - 1, special = false), book.condense.inputs.toLong())),
                ))
            }
        }
    }

    private fun books(job: Job, heroClass: String): List<Recipe> =
        index.skills.ofClass(heroClass).filter { it.unlock <= job.band.single() }.map { Recipe(it.book, job.copy(kind = JobKind.ITEM, output = it.book)) }
}
