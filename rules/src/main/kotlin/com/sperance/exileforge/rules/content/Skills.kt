package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.ceil

@Serializable enum class SkillKind { ACTIVE, PASSIVE }

@Serializable
enum class SkillType(val kind: SkillKind) {
    ATTACK(SkillKind.ACTIVE), SPELL(SkillKind.ACTIVE), WARCRY(SkillKind.ACTIVE), CURSE(SkillKind.ACTIVE),
    HEAL(SkillKind.ACTIVE), GUARD(SkillKind.ACTIVE), AURA(SkillKind.PASSIVE), BONUS(SkillKind.PASSIVE), TRIGGER(SkillKind.PASSIVE),
}

@Serializable
enum class SlotCondition {
    READY, FIGHT_START, RARE_OR_BOSS, LIFE_50, LIFE_35, LIFE_20, MANA_30, SHIELD_BROKEN, ENEMIES_3, AILING, MANUAL;
    val flaskOnly: Boolean get() = this == MANA_30
}

@Serializable enum class SkillEvent { KILL, SPELL_KILL, CRIT, SPELL_CRIT, EVADE, BLOCK, HIT_TAKEN, LOW_LIFE, SHIELD_BROKEN, HEALED, SKILL_USE }

/** Число умения на 1-м и 20-м уровне, между ними - прямая; в файле `[от, до]` или одно число. */
@Serializable(with = ScaleSerializer::class)
data class Scale(val from: Double, val to: Double = from) {
    fun at(level: Int): Double = from + (to - from) * (level - 1).coerceAtLeast(0) / (SkillRules.MAX_LEVEL - 1)
}

object ScaleSerializer : KSerializer<Scale> {
    private val list = ListSerializer(Double.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("Scale", list.descriptor)
    override fun serialize(encoder: Encoder, value: Scale) = encoder.encodeSerializableValue(list, listOf(value.from, value.to))
    override fun deserialize(decoder: Decoder): Scale = decoder.decodeSerializableValue(list).let { Scale(it.first(), it.getOrElse(1) { _ -> it.first() }) }
}

@Serializable data class SkillStat(val stat: String, val op: Op = Op.ADD, val value: Scale)
@Serializable data class SkillAilment(val ailment: String, val chance: Scale)
@Serializable data class SpellDamage(val element: String, val min: Scale, val max: Scale)

@Serializable
data class SkillHit(
    val targets: Int = 1, val hits: Int = 1, val weapon: Scale? = null, val spell: SpellDamage? = null, val element: String? = null,
    val convert: Scale? = null, val ailments: List<SkillAilment> = emptyList(), val stats: List<SkillStat> = emptyList(),
    val finisher: Scale? = null, val stun: Scale? = null,
)

@Serializable data class SkillDot(val targets: Int = 1, val element: String, val min: Scale, val max: Scale, val duration: Double, val ailments: List<SkillAilment> = emptyList())
@Serializable data class SkillBuff(val duration: Double, val stats: List<SkillStat> = emptyList(), val counter: Scale? = null, val nextCrit: Boolean = false)
@Serializable data class SkillCurse(val duration: Double, val targets: Int = 0, val stats: List<SkillStat>)
@Serializable data class SkillHeal(val life: Scale? = null, val mana: Scale? = null, val cleanse: Boolean = false)
@Serializable data class SkillBarrier(val life: Scale, val duration: Double)

@Serializable
data class SkillTrigger(
    val on: SkillEvent, val chance: Scale? = null, val cooldown: Double = 0.0, val heal: SkillHeal? = null, val shield: Scale? = null,
    val barrier: SkillBarrier? = null, val buff: SkillBuff? = null, val hit: SkillHit? = null, val flaskCharges: Int = 0,
    val ailment: String? = null, val twice: Boolean = false, val refund: Boolean = false,
)

/**
 * Заряды умения (1.32.0). Генератор: [gain] - сколько зарядов вида [kind] даёт применение (удар умения попал хотя бы по одному врагу,
 * клич или иное без удара - при применении); `RANDOM` - каждый наугад. Трата: [consume] - применение снимает все заряды вида [kind]
 * (`ALL` - всех трёх видов), урон удара умения растёт на [perCharge]% за каждый снятый заряд.
 */
@Serializable
data class SkillCharges(val kind: ChargeKind, val gain: Scale? = null, val consume: Boolean = false, val perCharge: Scale? = null)

@Serializable
data class SkillDefinition(
    val code: String, val heroClass: String, val type: SkillType, val unlock: Int, val icon: String,
    val mana: Scale? = null, val cooldown: Double = 0.0, val reserve: Double = 0.0, val condition: SlotCondition = SlotCondition.READY,
    val hit: SkillHit? = null, val dot: SkillDot? = null, val buff: SkillBuff? = null, val curse: SkillCurse? = null, val heal: SkillHeal? = null,
    val shield: Scale? = null, val barrier: SkillBarrier? = null, val stats: List<SkillStat> = emptyList(), val lowLife: Boolean = false,
    val trigger: SkillTrigger? = null,
    /** Подготовка (3.13.0 клиента): сколько процентов перезарядки идёт в начале боя, от первого уровня умения к последнему. */
    val prepare: Scale? = null,
    /** Заряды героя (1.32.0): даёт или тратит. */
    val charges: SkillCharges? = null,
) {
    val kind: SkillKind get() = type.kind
    val book: String get() = SkillRules.book(code)
}

@Serializable
data class MonsterSkill(
    val code: String, val icon: String, val mana: Double, val cooldown: Double, val spell: Boolean = true,
    val hit: SkillHit? = null, val buff: SkillBuff? = null, val curse: SkillCurse? = null, val heal: SkillHeal? = null, val manaBurn: Double = 0.0,
)

@Serializable data class ClassSkills(val code: String, val attributes: List<String>, val mana: Double)
@Serializable data class BookRule(val boss: Double, val bossOwnClass: Double, val rare: Double, val guardian: Double, val reach: Int)
@Serializable data class ExchangeRule(val books: Int, val goldPerLevel: Long)
@Serializable data class AttributeRule(val single: List<Double>, val dual: List<Double>, val triple: List<Double>)

@Serializable
data class SkillBookRules(
    val activeSlots: List<Int>, val passiveSlots: List<Int>, val manaPerLevel: Double, val attributes: AttributeRule,
    val books: BookRule, val exchange: ExchangeRule, val casterSpells: Map<String, String> = emptyMap(),
)

/** Файл `skills.json`: правила, классы, умения классов и монстров. */
@Serializable
data class SkillBook(val rules: SkillBookRules, val classes: List<ClassSkills>, val skills: List<SkillDefinition>, val monsterSkills: List<MonsterSkill>) {
    val byCode: Map<String, SkillDefinition> by lazy { skills.associateBy { it.code } }
    val classesByCode: Map<String, ClassSkills> by lazy { classes.associateBy { it.code } }
    val monsterByCode: Map<String, MonsterSkill> by lazy { monsterSkills.associateBy { it.code } }

    fun ofClass(heroClass: String): List<SkillDefinition> = skills.filter { it.heroClass == heroClass }
    fun byBook(itemCode: String): SkillDefinition? = itemCode.takeIf { it.startsWith(SkillRules.BOOK_PREFIX) }?.let { byCode[it.removePrefix(SkillRules.BOOK_PREFIX)] }

    fun validate(stats: StatRegistry, classes: Collection<String>) {
        if (rules.activeSlots.isEmpty() || rules.passiveSlots.isEmpty() || rules.activeSlots.first() != 1 || rules.passiveSlots.first() != 1) fail("skills: slots")
        if (rules.activeSlots.zipWithNext().any { (a, b) -> a >= b } || rules.passiveSlots.zipWithNext().any { (a, b) -> a >= b }) fail("skills: slot order")
        listOf(rules.attributes.single, rules.attributes.dual, rules.attributes.triple).forEach { if (it.size != 2) fail("skills: attributes") }
        if (this.classes.map { it.code }.toSet() != classes.toSet()) fail("skills: classes ${this.classes.map { it.code }} vs $classes")
        this.classes.forEach { c -> if (c.attributes.size !in 1..3 || c.mana <= 0 || c.attributes.any { it !in stats }) fail("skills: class ${c.code}") }
        val codes = skills.map { it.code }
        if (codes.toSet().size != codes.size) fail("skills: skill codes")
        val known = this.classes.map { it.code }.toSet()
        skills.forEach { validate(it, known, stats) }
        this.classes.forEach { heroClass ->
            val own = ofClass(heroClass.code)
            val charged = own.count { it.charges != null }
            if (own.count { it.kind == SkillKind.ACTIVE && it.charges == null } != ACTIVE_PER_CLASS || own.count { it.kind == SkillKind.PASSIVE } != PASSIVE_PER_CLASS ||
                charged > CHARGE_SKILLS_PER_CLASS) fail("skills: skills of ${heroClass.code}")
            if (own.none { it.kind == SkillKind.ACTIVE && it.unlock == 1 } || own.none { it.kind == SkillKind.PASSIVE && it.unlock == 1 }) fail("skills: first skills of ${heroClass.code}")
        }
        val monster = monsterSkills.map { it.code }
        if (monster.toSet().size != monster.size || monster.any { it in codes }) fail("skills: monster skill codes")
        monsterSkills.forEach { skill ->
            if (skill.cooldown <= 0 || skill.mana < 0 || listOfNotNull(skill.hit, skill.buff, skill.curse, skill.heal).isEmpty() && skill.manaBurn <= 0) fail("skills: monster skill ${skill.code}")
            skill.hit?.let { hit(it, skill.code) }
        }
        rules.casterSpells.values.forEach { if (it !in monster) fail("skills: caster spell $it") }
    }

    private fun validate(skill: SkillDefinition, classes: Set<String>, stats: StatRegistry) {
        val code = skill.code
        if (skill.heroClass !in classes) fail("skills: class of $code")
        if (skill.unlock !in 1..SkillRules.MAX_HERO_LEVEL) fail("skills: unlock of $code")
        if (skill.icon.isBlank()) fail("skills: icon of $code")
        when (skill.kind) {
            SkillKind.ACTIVE -> {
                if (skill.mana == null || skill.cooldown <= 0) fail("skills: cost of $code")
                if (listOfNotNull(skill.hit, skill.dot, skill.buff, skill.curse, skill.heal, skill.shield, skill.barrier).isEmpty()) fail("skills: action of $code")
                if (skill.condition.flaskOnly) fail("skills: condition of $code")
            }
            SkillKind.PASSIVE -> {
                if (skill.mana != null || skill.cooldown > 0) fail("skills: cost of passive $code")
                when (skill.type) {
                    SkillType.AURA -> if (skill.reserve !in 1.0..100.0 || skill.stats.isEmpty()) fail("skills: aura $code")
                    SkillType.BONUS -> if (skill.stats.isEmpty()) fail("skills: bonus $code")
                    else -> if (skill.trigger == null) fail("skills: trigger $code")
                }
            }
        }
        skill.hit?.let { hit(it, code) }
        skill.trigger?.hit?.let { hit(it, code) }
        skill.charges?.let { charges ->
            if (skill.kind != SkillKind.ACTIVE || charges.consume == (charges.gain != null)) fail("skills: charges of $code either gained or spent by an active skill")
            if (charges.gain != null && (charges.kind == ChargeKind.ALL || charges.gain.from < 1)) fail("skills: charges gained by $code")
            if (charges.consume && (charges.kind == ChargeKind.RANDOM || charges.perCharge == null || skill.hit == null)) fail("skills: charges spent by $code")
        }
        skill.dot?.let { if (it.element !in ELEMENTS || it.duration <= 0) fail("skills: dot of $code") }
        (skill.stats + skill.buff?.stats.orEmpty() + skill.curse?.stats.orEmpty() + skill.trigger?.buff?.stats.orEmpty()).forEach { stat ->
            if (stat.stat !in stats) fail("skills: stat ${stat.stat} of $code")
        }
    }

    private fun hit(hit: SkillHit, code: String) {
        if (hit.targets < 0 || hit.hits < 1 || (hit.weapon == null) == (hit.spell == null)) fail("skills: hit of $code")
        hit.spell?.let { if (it.element !in ELEMENTS) fail("skills: spell element of $code") }
        hit.element?.let { if (it !in ELEMENTS + RANDOM) fail("skills: element of $code") }
        hit.ailments.forEach { if (it.ailment !in AILMENTS) fail("skills: ailment of $code") }
    }

    companion object {
        const val ACTIVE_PER_CLASS = 6
        const val PASSIVE_PER_CLASS = 8
        /** Умений зарядов (1.32.0) на класс - сверх [ACTIVE_PER_CLASS] активных. */
        const val CHARGE_SKILLS_PER_CLASS = 1
        private const val RANDOM = "RANDOM"
        val ELEMENTS = setOf("PHYSICAL", "FIRE", "COLD", "LIGHTNING", "CHAOS")
        val AILMENTS = setOf("IGNITE", "CHILL", "FREEZE", "SHOCK", "POISON", "BLEED", "ELEMENT")
    }
}

@Serializable data class ActiveSlot(val skill: String, val condition: SlotCondition)

/** Умения героя: изученные уровни, слоты активных и пассивных, условия глотков фляг. */
@Serializable
data class HeroSkills(
    val learned: Map<String, Int> = emptyMap(),
    val active: List<ActiveSlot?> = emptyList(),
    val passive: List<String?> = emptyList(),
    val flasks: List<SlotCondition?> = emptyList(),
) {
    fun level(code: String): Int = learned[code] ?: 0
}

data class SkillNeed(val heroLevel: Int, val attributes: Map<String, Int>)

/** Правила книги умений: чистые функции над [SkillBook]. */
class SkillRules(val book: SkillBook) {
    private val rules get() = book.rules

    fun heroLevel(skill: SkillDefinition, level: Int): Int =
        ceil(skill.unlock + (MAX_HERO_LEVEL - skill.unlock) * (level - 1) / (MAX_LEVEL - 1.0) - 1e-9).toInt()

    fun need(skill: SkillDefinition, level: Int): SkillNeed {
        val heroLevel = heroLevel(skill, level)
        val heroClass = book.classesByCode.getValue(skill.heroClass)
        val (factor, plus) = when (heroClass.attributes.size) { 1 -> rules.attributes.single; 2 -> rules.attributes.dual; else -> rules.attributes.triple }
        val amount = ceil(factor * heroLevel + plus - 1e-9).toInt()
        return SkillNeed(heroLevel, heroClass.attributes.associateWith { amount })
    }

    fun unmet(skill: SkillDefinition, level: Int, heroLevel: Int, stats: Map<String, Double>): List<String> {
        val need = need(skill, level)
        return listOfNotNull("level ${need.heroLevel}".takeIf { heroLevel < need.heroLevel }) +
            need.attributes.filter { (stat, amount) -> (stats[stat] ?: 0.0) < amount }.map { (stat, amount) -> "$stat $amount" }
    }

    fun activeSlots(heroLevel: Int): Int = rules.activeSlots.count { it <= heroLevel }
    fun passiveSlots(heroLevel: Int): Int = rules.passiveSlots.count { it <= heroLevel }
    fun slotLevel(kind: SkillKind, index: Int): Int? = (if (kind == SkillKind.ACTIVE) rules.activeSlots else rules.passiveSlots).getOrNull(index)
    fun mana(heroClass: String, level: Int): Double = (book.classesByCode[heroClass]?.mana ?: 0.0) + rules.manaPerLevel * level

    fun starter(heroClass: String): HeroSkills {
        val own = book.ofClass(heroClass)
        val active = own.first { it.kind == SkillKind.ACTIVE && it.unlock == 1 }
        val passive = own.first { it.kind == SkillKind.PASSIVE && it.unlock == 1 }
        return HeroSkills(mapOf(active.code to 1, passive.code to 1), listOf(ActiveSlot(active.code, active.condition)), listOf(passive.code))
    }

    /** Книга с монстра: шанс [chance], умение открытое не выше зоны + reach, своего класса с долей [ownShare]. */
    fun dropBook(heroClass: String, zoneLevel: Int, chance: Double, ownShare: Double, dice: com.sperance.exileforge.rules.roll.Dice): String? {
        if (!dice.chance(chance)) return null
        val open = book.skills.filter { it.unlock <= zoneLevel + rules.books.reach }
        val own = open.filter { it.heroClass == heroClass }
        val from = if (own.isNotEmpty() && dice.chance(ownShare)) own else open
        return dice.pickOrNull(from)?.book
    }

    companion object {
        const val MAX_LEVEL = 20
        /** Потолок уровня умения с прибавками вещей, атласа и карты (1.17.0): дальше шкала не тянется. */
        const val MAX_BOOSTED_LEVEL = 25
        const val MAX_HERO_LEVEL = 70
        const val BOOK_PREFIX = "BOOK_"
        fun book(code: String): String = BOOK_PREFIX + code
    }
}
