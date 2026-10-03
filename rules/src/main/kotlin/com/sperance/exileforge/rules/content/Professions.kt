package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

@Serializable data class JobExtra(val item: String, val chance: Double)

@Serializable data class JobInput(val item: String, val amount: Long)

/**
 * Вид работы. С выбором (1.43.0, см. [JobRecipes]) - [CONDENSE], [BOOK], [REFINE] и [EQUIPMENT]: игрок называет,
 * что делать (ступень и вид эссенции, умение своего класса, что перегнать, группа и атрибут вещи кузнеца), и работа
 * получает свой вход и выход. [MAP] чертит случайную открытую зону своего региона, [JEWEL] - гранит самоцвет (1.44.0).
 */
@Serializable enum class JobKind {
    ITEM,
    EQUIPMENT,
    MAP,
    FLASK,
    BOOK,
    CONDENSE,
    REFINE,
    JEWEL,
    ;

    val chosen: Boolean get() = this == BOOK || this == CONDENSE || this == REFINE || this == EQUIPMENT
}

@Serializable
data class Job(
    val code: String,
    val level: Int,
    val seconds: Double,
    val nothing: Double,
    val output: String,
    val experience: Double,
    val extra: List<JobExtra> = emptyList(),
    val kind: JobKind = JobKind.ITEM,
    val inputs: List<JobInput> = emptyList(),
    val band: List<Int> = emptyList(),
    val region: String = "",
    val additives: Boolean = false,
    /** Прибавка опыта за ступень выбора сверх первой (сгущение эссенций). */
    val step: Double = 0.0,
    /** Цепочка добычи (1.44.0): выходы работ одной цепочки перегоняются [REFINE] по порядку уровней. */
    val chain: String = "",
    /** Перегонка (1.44.0): сколько единиц низшей ступени идёт на одну высшую. */
    val ratio: Long = 0,
    /** Картограф (1.44.0): карта с тиром до этого - только зоны не ниже [MapTierRule.fromLevel]. */
    val tier: Int = 0,
)

/** Правила ремесла: шансы ручной работы и уникалки, таблицы баз, уникалок, строк и редкостей кузнеца и картографа. */
@Serializable
data class CraftingRules(
    val handcraftedChance: Double = 20.0,
    val maxHandcrafted: Int = 3,
    val maxAdditives: Int = 2,
    val uniqueChance: Double = 0.5,
    val smithRarities: String = "rarity:smith",
    val mapRarities: String = "rarity:map:smith",
    val mapHandcraftedChance: Double = 25.0,
    val flaskMagicChance: Double = 20.0,
    val additives: Map<String, String> = emptyMap(),
    val tables: List<String> = emptyList(),
    val uniques: List<String> = emptyList(),
    val modifiers: List<String> = emptyList(),
    val mapModifiers: List<String> = emptyList(),
)

@Serializable data class Profession(val code: String, val tool: Slot, val jobs: List<Job>)

@Serializable
data class CraftsRules(
    val offlineHours: Double,
    val maxLevel: Int,
    val levelSpeed: Double,
    val levelFind: Double,
    val luckCap: Double,
    val experienceBase: Double,
    val experiencePower: Double,
    /** Отлучка короче стольких минут не пишется в журнал работ. */
    val awayMinMinutes: Int,
) {
    val awayMinMillis: Long get() = awayMinMinutes * 60_000L
}

/** Файл `professions.json`. */
@Serializable
data class CraftsFile(val rules: CraftsRules, val professions: List<Profession>, val crafting: CraftingRules = CraftingRules()) {
    val jobs: Map<String, Pair<Profession, Job>> by lazy { professions.flatMap { p -> p.jobs.map { it.code to (p to it) } }.toMap() }

    fun profession(code: String): Profession? = professions.firstOrNull { it.code == code }

    fun validate(items: (String) -> Boolean, template: (String) -> ItemTemplate?, region: (String) -> Boolean) {
        rules.let { r ->
            if (r.offlineHours <= 0 || r.maxLevel < 2 || r.levelSpeed !in 0.0..90.0 || r.levelFind < 0 || r.luckCap !in 0.0..100.0 || r.experienceBase <= 0 || r.experiencePower <= 0) fail("professions: rules")
        }
        if (professions.map { it.code }.toSet().size != professions.size) fail("professions: codes")
        if (professions.any { !it.tool.isTool }) fail("professions: tool slots")
        if (professions.map { it.tool }.toSet().size != professions.size) fail("professions: one tool per profession")
        val all = professions.flatMap { it.jobs }
        if (all.map { it.code }.toSet().size != all.size) fail("professions: job codes")
        all.forEach { job ->
            if (job.level !in 1..rules.maxLevel || job.seconds <= 0 || job.nothing !in 0.0..100.0 || job.experience < 0) fail("professions: job ${job.code}")
            if (job.extra.any { it.chance !in 0.0..100.0 || !items(it.item) }) fail("professions: extra of ${job.code}")
            if (job.inputs.any { it.amount <= 0 || !items(it.item) }) fail("professions: inputs of ${job.code}")
            when (job.kind) {
                JobKind.ITEM -> if (!items(job.output)) fail("professions: output of ${job.code}")
                JobKind.EQUIPMENT -> if (job.band.size != 2 || job.band[0] > job.band[1]) fail("professions: band of ${job.code}")
                JobKind.MAP -> if (!region(job.region)) fail("professions: region of ${job.code}")
                JobKind.FLASK -> if (template(job.output)?.slot?.isFlask != true) fail("professions: flask of ${job.code}")
                JobKind.BOOK -> if (job.band.size != 1 || job.output.isNotEmpty()) fail("professions: book of ${job.code}")
                JobKind.CONDENSE -> if (job.output.isNotEmpty() || job.inputs.isNotEmpty() || job.step < 0) fail("professions: condense of ${job.code}")
                JobKind.REFINE -> if (job.output.isNotEmpty() || job.inputs.isNotEmpty() || job.ratio < 2) fail("professions: refine of ${job.code}")
                JobKind.JEWEL -> if (job.band.size != 2 || job.band[0] > job.band[1]) fail("professions: jewel band of ${job.code}")
            }
        }
        professions.forEach { if (it.jobs.none { job -> job.level == 1 }) fail("professions: no first-level work in ${it.code}") }
        crafting.let { c ->
            if (c.handcraftedChance !in 0.0..100.0 || c.mapHandcraftedChance !in 0.0..100.0 || c.uniqueChance !in 0.0..100.0) fail("professions: crafting chances")
            if (c.maxHandcrafted < 1 || c.maxAdditives !in 0..c.maxHandcrafted) fail("professions: crafting limits")
            if (listOf(c.tables, c.uniques, c.modifiers, c.mapModifiers).any { it.isEmpty() }) fail("professions: crafting tables")
            c.additives.forEach { (item, _) -> if (!items(item)) fail("professions: additive $item") }
        }
    }
}
