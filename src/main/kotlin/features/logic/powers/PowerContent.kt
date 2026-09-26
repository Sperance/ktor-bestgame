package features.logic.powers

import application.enums.EnumMonsterRarity
import application.enums.EnumStatStock
import application.enums.IntEnumStat
import base.exception.model.EquipmentExceptions
import config.ContentResource
import kotlinx.serialization.json.Json

/**
 * Книга сил из `content/powers.json` (0.70.0). Файл - не код, поэтому книга проверяется при чтении:
 * у каждого стата `POWER_*` ровно одна запись и наоборот, правила листа называют существующие статы,
 * а каждое число, которое действие оставило пустым, есть чем заполнить.
 */
object PowerContent {
    const val FILE = "powers.json"
    const val PREFIX = "POWER_"

    private val json = Json { ignoreUnknownKeys = true }

    val book: PowerBook by lazy { load(ContentResource.read(FILE)) }

    val byStat: Map<String, Power> by lazy { book.powers.associateBy { it.stat } }

    fun load(text: String): PowerBook = json.decodeFromString(PowerBook.serializer(), text).also(::validate)

    private fun validate(book: PowerBook) {
        fun fail(what: String): Nothing = throw EquipmentExceptions.funException("PowerContent", what)
        val stats = EnumStatStock.entries.map { it.name }.toSet()
        val declared = stats.filter { it.startsWith(PREFIX) }.toSet()
        val codes = book.powers.map { it.stat }
        if (codes.toSet().size != codes.size) fail("duplicate powers")
        if (codes.toSet() != declared) fail("powers without a stat or stats without a power: ${(codes.toSet() - declared) + (declared - codes.toSet())}")
        book.powers.forEach { power ->
            val code = power.stat
            if (power.sheet.isEmpty() && power.on == null && power.world == null) fail("$code does nothing")
            power.sheet.forEach { rule ->
                if (rule.to !in stats || rule.from?.let { it !in stats } == true) fail("$code sheet names an unknown stat")
                if ((rule.op == SheetOp.CONVERT || rule.op == SheetOp.GAIN || rule.op == SheetOp.PER) && rule.from == null) fail("$code ${rule.op} needs a source")
                if (rule.op == SheetOp.PER && rule.per <= 0) fail("$code PER needs a step")
            }
            if (power.on == PowerEvent.EVERY && power.every <= 0) fail("$code EVERY needs a period")
            if (power.on == PowerEvent.STANDING && power.effects.any { it.act != PowerAct.BUFF || it.duration != null }) fail("$code standing lays lines only")
            if (power.roll == PowerRoll.CHANCE && power.chance != null) fail("$code rolls its chance and sets it too")
            power.effects.forEach { effect ->
                val rolled = power.roll == PowerRoll.AMOUNT
                effect.lines.forEach { line -> if (line.stat !in stats || (line.value == null && !rolled)) fail("$code line ${line.stat}") }
                if (effect.act in AMOUNTED && effect.amount == null && !rolled) fail("$code ${effect.act} has no amount")
                if (effect.act in TIMED && effect.duration == null && power.roll != PowerRoll.DURATION && power.on != PowerEvent.STANDING) fail("$code ${effect.act} has no duration")
                if (effect.act == PowerAct.AILMENT && effect.ailment == null) fail("$code AILMENT names none")
            }
            power.world?.against?.forEach { rarity -> if (EnumMonsterRarity.entries.none { it.name == rarity }) fail("$code against $rarity") }
        }
    }

    private val AMOUNTED = setOf(PowerAct.HEAL, PowerAct.HURT, PowerAct.BARRIER, PowerAct.DAMAGE, PowerAct.EXECUTE, PowerAct.CHARGES, PowerAct.COOLDOWNS)
    private val TIMED = setOf(PowerAct.BUFF, PowerAct.HEX, PowerAct.BARRIER, PowerAct.STUN, PowerAct.DELAY, PowerAct.INVULNERABLE)
}

/**
 * Правила листа (0.70.0): после свода модификаторов, по порядку книги, для каждой силы, чей стат на листе
 * не ноль. Тот же проход делает клиент над своим листом - поэтому округление то же, что у [features.logic.modifiers.ModifierMath].
 */
object SheetPowers {
    private val sheetPowers: List<Power> by lazy { PowerContent.book.powers.filter { it.sheet.isNotEmpty() } }
    private val byName: Map<String, IntEnumStat> by lazy { EnumStatStock.entries.associateBy { it.name } }

    fun apply(stats: MutableMap<IntEnumStat, Double>): MutableMap<IntEnumStat, Double> {
        sheetPowers.forEach { power ->
            val rolled = stats[byName.getValue(power.stat)] ?: 0.0
            if (rolled == 0.0) return@forEach
            power.sheet.forEach { rule ->
                val to = byName.getValue(rule.to)
                val from = rule.from?.let(byName::getValue)
                val source = from?.let { stats[it] } ?: 0.0
                val value = rule.value ?: rolled
                stats[to] = when (rule.op) {
                    SheetOp.CONVERT -> {
                        val moved = source * value.coerceIn(0.0, 100.0) / 100
                        stats[from!!] = round(source - moved)
                        (stats[to] ?: 0.0) + moved * rule.factor
                    }
                    SheetOp.GAIN -> (stats[to] ?: 0.0) + source * value / 100 * rule.factor
                    SheetOp.PER -> (stats[to] ?: 0.0) + kotlin.math.floor(source / rule.per) * value
                    SheetOp.SET -> value
                    SheetOp.MORE -> (stats[to] ?: 0.0) * (1 + value / 100)
                }.let(::round)
            }
        }
        return stats
    }

    private fun round(value: Double): Double = java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).toDouble()
}

/**
 * Небоевые силы (0.70.0): сумма роллов сил вида [kind] на листе героя, что действуют на монстра
 * редкости [rarity] - прибавка в процентах к опыту, количеству, редкости, золоту или шансам.
 */
object WorldPowers {
    private val worldPowers: List<Power> by lazy { PowerContent.book.powers.filter { it.world != null } }
    private val byName: Map<String, IntEnumStat> by lazy { EnumStatStock.entries.associateBy { it.name } }

    fun bonus(sheet: Map<IntEnumStat, Double>, kind: WorldKind, rarity: EnumMonsterRarity? = null): Double =
        worldPowers.filter { power -> power.world!!.gain == kind && (power.world.against.isEmpty() || rarity?.name in power.world.against) }
            .sumOf { sheet[byName.getValue(it.stat)] ?: 0.0 }

    /** Шанс, поднятый силами вида [kind]: `chance × (1 + бонус/100)`. */
    fun chance(sheet: Map<IntEnumStat, Double>, kind: WorldKind, chance: Double, rarity: EnumMonsterRarity? = null): Double =
        chance * (1 + bonus(sheet, kind, rarity) / 100)
}
