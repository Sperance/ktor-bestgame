package features.logic.skills

import application.enums.EnumModifierOperation
import application.enums.EnumStatStock
import application.enums.IntEnumStat
import base.exception.model.SkillExceptions
import config.ContentResource
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** Вид умения (с 0.69.0): активное ставится в один из трёх слотов, пассивное - в один из двух. */
enum class SkillKind { ACTIVE, PASSIVE }

/** Тип умения: у активного - как оно действует в бою, у пассивного - аура с резервом, даровой бонус или срабатывание. */
enum class SkillType(val kind: SkillKind) {
    ATTACK(SkillKind.ACTIVE), SPELL(SkillKind.ACTIVE), WARCRY(SkillKind.ACTIVE), CURSE(SkillKind.ACTIVE),
    HEAL(SkillKind.ACTIVE), GUARD(SkillKind.ACTIVE), AURA(SkillKind.PASSIVE), BONUS(SkillKind.PASSIVE), TRIGGER(SkillKind.PASSIVE),
}

/**
 * Когда автобой сам применяет умение слота или пьёт флягу. [MANA_30] - только для фляг; [MANUAL] -
 * только тапом. Условие берётся с умения по умолчанию, игрок меняет его на слоте.
 */
enum class SlotCondition {
    READY, FIGHT_START, RARE_OR_BOSS, LIFE_50, LIFE_35, LIFE_20, MANA_30, SHIELD_BROKEN, ENEMIES_3, AILING, MANUAL;

    val flaskOnly: Boolean get() = this == MANA_30
}

/** Событие боя, на которое срабатывает пассив. */
enum class SkillEvent { KILL, SPELL_KILL, CRIT, SPELL_CRIT, EVADE, BLOCK, HIT_TAKEN, LOW_LIFE, SHIELD_BROKEN, HEALED, SKILL_USE }

/**
 * Число умения на 1-м и 20-м уровне; между ними и выше двадцатого (уровни от экипировки) - ровная прямая.
 * В файле это массив `[на 1-м, на 20-м]` или одно число, если умение его не растит.
 */
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

/** Строка характеристики умения: как модификатор, только значение растёт с уровнем. */
@Serializable
data class SkillStat(val stat: IntEnumStat, val operation: EnumModifierOperation = EnumModifierOperation.ADD, val value: Scale)

/** Шанс состояния от удара умения; `ELEMENT` - состояние стихии, которой ударили. */
@Serializable
data class SkillAilment(val ailment: String, val chance: Scale)

/** Урон чар: стихия и базовый разброс, от которого считаются увеличения. */
@Serializable
data class SpellDamage(val element: String, val min: Scale, val max: Scale)

/**
 * Удар умения. [targets] - сколько врагов, 0 - все; [weapon] - процент урона оружия, [spell] - свой урон чар.
 * [element] с [convert] - какая доля урона оружия уходит в стихию (`RANDOM` - случайная стихия); [finisher] -
 * процент урона оружия по цели под состоянием или со здоровьем ниже 30 %.
 */
@Serializable
data class SkillHit(
    val targets: Int = 1,
    val hits: Int = 1,
    val weapon: Scale? = null,
    val spell: SpellDamage? = null,
    val element: String? = null,
    val convert: Scale? = null,
    val ailments: List<SkillAilment> = emptyList(),
    val stats: List<SkillStat> = emptyList(),
    val finisher: Scale? = null,
    /** Шанс оглушить ударом, в процентах, сверх правила порога. */
    val stun: Scale? = null,
)

/** Урон со временем: [min]..[max] за всё время [duration], с шансами состояний [ailments]. */
@Serializable
data class SkillDot(val targets: Int = 1, val element: String, val min: Scale, val max: Scale, val duration: Double,
                    val ailments: List<SkillAilment> = emptyList())

/** Бафф героя на [duration] секунд; [counter] - ответ на блок этим процентом урона оружия, [nextCrit] - следующий удар критический. */
@Serializable
data class SkillBuff(val duration: Double, val stats: List<SkillStat> = emptyList(), val counter: Scale? = null, val nextCrit: Boolean = false)

/** Проклятие: [stats] ложатся на [targets] врагов (0 - на всех) на [duration] секунд. */
@Serializable
data class SkillCurse(val duration: Double, val targets: Int = 0, val stats: List<SkillStat>)

/** Лечение: проценты максимума здоровья и маны, [cleanse] - снять один недуг. */
@Serializable
data class SkillHeal(val life: Scale? = null, val mana: Scale? = null, val cleanse: Boolean = false)

/** Барьер: поглощает урон на [life] процентов максимума здоровья, пока не истекут [duration] секунд. */
@Serializable
data class SkillBarrier(val life: Scale, val duration: Double)

/**
 * Срабатывание пассива: на событие [on] с шансом [chance] (без него - всегда), не чаще раза в [cooldown] секунд.
 * Что делает - одно или несколько: лечит, возвращает щит, ставит барьер или бафф, бьёт, даёт заряд фляг,
 * накладывает состояние (дважды - [twice]), возвращает ману умения ([refund]).
 */
@Serializable
data class SkillTrigger(
    val on: SkillEvent,
    val chance: Scale? = null,
    val cooldown: Double = 0.0,
    val heal: SkillHeal? = null,
    val shield: Scale? = null,
    val barrier: SkillBarrier? = null,
    val buff: SkillBuff? = null,
    val hit: SkillHit? = null,
    val flaskCharges: Int = 0,
    val ailment: String? = null,
    val twice: Boolean = false,
    val refund: Boolean = false,
)

/**
 * Умение класса. Активное: цена в мане, перезарядка, условие по умолчанию и действие - удар, урон со
 * временем, бафф, проклятие, лечение, щит или барьер. Пассивное: аура держит [reserve] процентов
 * максимума маны, бонус даёт [stats] даром ([lowLife] - только пока здоровья меньше половины),
 * срабатывание - [trigger]. Книга умения - предмет `BOOK_<код>`.
 */
@Serializable
data class SkillDefinition(
    val code: String,
    val heroClass: String,
    val type: SkillType,
    val unlock: Int,
    val icon: String,
    val mana: Scale? = null,
    val cooldown: Double = 0.0,
    val reserve: Double = 0.0,
    val condition: SlotCondition = SlotCondition.READY,
    val hit: SkillHit? = null,
    val dot: SkillDot? = null,
    val buff: SkillBuff? = null,
    val curse: SkillCurse? = null,
    val heal: SkillHeal? = null,
    val shield: Scale? = null,
    val barrier: SkillBarrier? = null,
    val stats: List<SkillStat> = emptyList(),
    val lowLife: Boolean = false,
    val trigger: SkillTrigger? = null,
) {
    val kind: SkillKind get() = type.kind
    val book: String get() = SkillRules.book(code)
}

/** Умение монстра: боссы и заклинатели колдуют за свою ману; урон - процент своего удара в стихии [SkillHit.element]. */
@Serializable
data class MonsterSkill(
    val code: String,
    val icon: String,
    val mana: Double,
    val cooldown: Double,
    val spell: Boolean = true,
    val hit: SkillHit? = null,
    val buff: SkillBuff? = null,
    val curse: SkillCurse? = null,
    val heal: SkillHeal? = null,
    val manaBurn: Double = 0.0,
)

/** Класс в книге умений: какие характеристики просят его книги и сколько маны у него на старте. */
@Serializable
data class ClassSkills(val code: String, val attributes: List<IntEnumStat>, val mana: Double)

/** Шансы книг умений: с босса (из них своего класса), редкого монстра и стража кристалла; не выше уровня зоны + [reach]. */
@Serializable
data class BookRule(val boss: Double, val bossOwnClass: Double, val rare: Double, val guardian: Double, val reach: Int)

/** Обмен книг: [books] любых и золото за уровень героя - на одну выбранную своего класса. */
@Serializable
data class ExchangeRule(val books: Int, val goldPerLevel: Long)

/**
 * Требования уровня умения к характеристикам: `множитель × требуемый уровень героя + прибавка` - у
 * класса одной характеристики [single], двух - [dual] каждая, трёх - [triple] каждая.
 */
@Serializable
data class AttributeRule(val single: List<Double>, val dual: List<Double>, val triple: List<Double>)

/** Правила книги: слоты открываются уровнем героя, мана - базой класса, уровнем и интеллектом. */
@Serializable
data class SkillBookRules(
    val activeSlots: List<Int>,
    val passiveSlots: List<Int>,
    val manaPerLevel: Double,
    val attributes: AttributeRule,
    val books: BookRule,
    val exchange: ExchangeRule,
    val casterSpells: Map<String, String> = emptyMap(),
)

/** Файл `skills.json` (0.69.0): правила, классы, умения классов и умения монстров. */
@Serializable
data class SkillBook(
    val rules: SkillBookRules,
    val classes: List<ClassSkills>,
    val skills: List<SkillDefinition>,
    val monsterSkills: List<MonsterSkill>,
)

/**
 * Книга умений - правила мира, а не состояние: файл читается при старте и проверяется при чтении,
 * как кампания. Клиенту она уходит целиком в `world.json` - бой считает он.
 */
object SkillContent {
    const val FILE = "skills.json"

    private val json = Json { ignoreUnknownKeys = true }

    val book: SkillBook by lazy { load(ContentResource.read(FILE)) }

    val skills: Map<String, SkillDefinition> by lazy { book.skills.associateBy { it.code } }

    val classes: Map<String, ClassSkills> by lazy { book.classes.associateBy { it.code } }

    val monsterSkills: Map<String, MonsterSkill> by lazy { book.monsterSkills.associateBy { it.code } }

    /** Умения класса по порядку книги: сначала активные, потом пассивные, каждые - по уровню открытия. */
    fun ofClass(heroClass: String): List<SkillDefinition> = book.skills.filter { it.heroClass == heroClass }

    /** Умение по коду книги `BOOK_<код>`; null - не книга. */
    fun byBook(itemCode: String): SkillDefinition? = itemCode.takeIf { it.startsWith(SkillRules.BOOK_PREFIX) }?.let { skills[it.removePrefix(SkillRules.BOOK_PREFIX)] }

    fun load(text: String): SkillBook = json.decodeFromString(SkillBook.serializer(), text).also(::validate)

    private fun validate(book: SkillBook) {
        val method = "skills"
        fun fail(what: String): Nothing = throw SkillExceptions.funExceptionContent(method, what)
        val rules = book.rules
        if (rules.activeSlots.isEmpty() || rules.passiveSlots.isEmpty() || rules.activeSlots.first() != 1 || rules.passiveSlots.first() != 1) fail("slots")
        if (rules.activeSlots.zipWithNext().any { (a, b) -> a >= b } || rules.passiveSlots.zipWithNext().any { (a, b) -> a >= b }) fail("slot order")
        listOf(rules.attributes.single, rules.attributes.dual, rules.attributes.triple).forEach { if (it.size != 2) fail("attributes") }
        if (book.classes.map { it.code }.toSet().size != book.classes.size) fail("class codes")
        book.classes.forEach { if (it.attributes.size !in 1..3 || it.mana <= 0) fail("class ${it.code}") }
        val codes = book.skills.map { it.code }
        if (codes.toSet().size != codes.size) fail("skill codes")
        val known = book.classes.map { it.code }.toSet()
        book.skills.forEach { skill -> validate(skill, known, ::fail) }
        book.classes.forEach { heroClass ->
            val own = book.skills.filter { it.heroClass == heroClass.code }
            if (own.count { it.kind == SkillKind.ACTIVE } != ACTIVE_PER_CLASS || own.count { it.kind == SkillKind.PASSIVE } != PASSIVE_PER_CLASS) fail("skills of ${heroClass.code}")
            if (own.none { it.kind == SkillKind.ACTIVE && it.unlock == 1 } || own.none { it.kind == SkillKind.PASSIVE && it.unlock == 1 }) fail("first skills of ${heroClass.code}")
        }
        val monster = book.monsterSkills.map { it.code }
        if (monster.toSet().size != monster.size || monster.any { it in codes }) fail("monster skill codes")
        book.monsterSkills.forEach { skill ->
            if (skill.cooldown <= 0 || skill.mana < 0 || listOfNotNull(skill.hit, skill.buff, skill.curse, skill.heal).isEmpty() && skill.manaBurn <= 0) fail("monster skill ${skill.code}")
            skill.hit?.let { hit(it, skill.code, ::fail) }
        }
        rules.casterSpells.values.forEach { if (it !in monster) fail("caster spell $it") }
    }

    private fun validate(skill: SkillDefinition, classes: Set<String>, fail: (String) -> Nothing) {
        val code = skill.code
        if (skill.heroClass !in classes) fail("class of $code")
        if (skill.unlock !in 1..SkillRules.MAX_HERO_LEVEL) fail("unlock of $code")
        if (skill.icon.isBlank()) fail("icon of $code")
        when (skill.kind) {
            SkillKind.ACTIVE -> {
                if (skill.mana == null || skill.cooldown <= 0) fail("cost of $code")
                if (listOfNotNull(skill.hit, skill.dot, skill.buff, skill.curse, skill.heal, skill.shield, skill.barrier).isEmpty()) fail("action of $code")
                if (skill.condition.flaskOnly) fail("condition of $code")
            }
            SkillKind.PASSIVE -> {
                if (skill.mana != null || skill.cooldown > 0) fail("cost of passive $code")
                when (skill.type) {
                    SkillType.AURA -> if (skill.reserve !in 1.0..100.0 || skill.stats.isEmpty()) fail("aura $code")
                    SkillType.BONUS -> if (skill.stats.isEmpty()) fail("bonus $code")
                    else -> if (skill.trigger == null) fail("trigger $code")
                }
            }
        }
        skill.hit?.let { hit(it, code, fail) }
        skill.trigger?.hit?.let { hit(it, code, fail) }
        skill.dot?.let { if (it.element !in ELEMENTS || it.duration <= 0) fail("dot of $code") }
        (skill.stats + skill.buff?.stats.orEmpty() + skill.curse?.stats.orEmpty() + skill.trigger?.buff?.stats.orEmpty()).forEach { stat ->
            if (stat.stat !is EnumStatStock) fail("stat of $code")
        }
    }

    private fun hit(hit: SkillHit, code: String, fail: (String) -> Nothing) {
        if (hit.targets < 0 || hit.hits < 1 || (hit.weapon == null) == (hit.spell == null)) fail("hit of $code")
        hit.spell?.let { if (it.element !in ELEMENTS) fail("spell element of $code") }
        hit.element?.let { if (it !in ELEMENTS + RANDOM) fail("element of $code") }
        hit.ailments.forEach { if (it.ailment !in AILMENTS) fail("ailment of $code") }
    }

    const val ACTIVE_PER_CLASS = 6
    const val PASSIVE_PER_CLASS = 8
    private const val RANDOM = "RANDOM"
    private val ELEMENTS = setOf("PHYSICAL", "FIRE", "COLD", "LIGHTNING", "CHAOS")
    private val AILMENTS = setOf("IGNITE", "CHILL", "FREEZE", "SHOCK", "POISON", "BLEED", "ELEMENT")
}
