package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode

/** Как значение ложится на характеристику: `итог = (база + ΣADD) × (1 + ΣINCREASED/100) × Π(1 + MORE/100)`, SET - последним. */
@Serializable
enum class Op { ADD, INCREASED, MORE, SET }

/**
 * Откуда модификатор берётся. Аффиксы (PREFIX, SUFFIX) катаются из таблиц носителя и занимают его места;
 * прочие либо закреплены носителем, либо ставятся своим путём и мест не занимают.
 */
@Serializable
enum class Source {
    IMPLICIT, PREFIX, SUFFIX, UNIQUE, ENCHANTMENT, CORRUPTION, PASSIVE, HANDCRAFTED, ALCHEMY, MONSTER, ESSENCE,
    /** Прибавка узла атласа: значения фиксирует узел, тиров нет. */
    ATLAS,
    /** Строка редкости монстра или базы класса: значения фиксированы носителем. */
    RULE;

    val affix: Boolean get() = this == PREFIX || this == SUFFIX

    /** Закреплённые носителем строки - сидят на каждой копии, сферы аффиксов их не трогают. */
    val permanent: Boolean get() = this == IMPLICIT || this == ENCHANTMENT || this == CORRUPTION || this == UNIQUE
}

/**
 * Вариант семейства: те же эффекты и текст, свой код `<семейство>@<вариант>`, свои тиры и правила.
 * Природный вариант носит код семейства как есть.
 */
@Serializable
enum class VariantKind(val suffix: String?) {
    NATURAL(null), LOCAL("LOCAL"), CRAFTED("CRAFTED"), IMPLICIT("IMPLICIT"), CORRUPTED("CORRUPTED"), ENCHANT("ENCHANT");

    companion object {
        const val SEPARATOR = "@"
        fun code(family: String, kind: VariantKind): String = kind.suffix?.let { "$family$SEPARATOR$it" } ?: family
        fun family(code: String): String = code.substringBefore(SEPARATOR)
        fun of(code: String): VariantKind = code.substringAfter(SEPARATOR, "").let { suffix -> entries.firstOrNull { it.suffix == suffix } ?: NATURAL }
    }
}

/**
 * Одно действие модификатора. С [perStat] это конверсия: значение умножается на то, сколько раз
 * [perAmount] укладывается в уже посчитанный источник; порядок источника обязан быть меньше порядка [stat].
 */
@Serializable
data class Effect(
    val stat: String,
    val op: Op = Op.ADD,
    val perStat: String? = null,
    val perAmount: Double = 1.0,
)

/** Диапазон `[min, max]` одного эффекта в тире. */
typealias Range = List<Double>

/**
 * Тир: [level] - уровень предмета (карты), с которого он открыт, [weight] - вес среди открытых
 * тиров, [values] - диапазон на каждый эффект. Тир 1 - первый в списке и лучший, как в PoE.
 */
@Serializable
data class Tier(val level: Int = 1, val weight: Int = 0, val values: List<Range> = emptyList()) {
    /** Значения тира при доле ролла [p]: 0 - дно, 1 - потолок; одна доля на все эффекты. */
    fun at(p: Double): List<Double> = values.map { (min, max) -> tenths(min + (max - min) * p) }

    fun problem(effects: Int): String? = when {
        level < 1 -> "level $level"
        weight < 0 -> "weight $weight"
        values.size != effects -> "$effects effects, ${values.size} values"
        values.any { it.size != 2 || it[0] > it[1] } -> "a [min, max] per effect"
        else -> null
    }
}

/**
 * Сетка тиров: уровни от лучшего к худшему, диапазоны лучшего ([top]) и худшего ([bottom]) тира
 * по эффекту, прямая между ними. Вес тира - `1000 × ratio^(позиция снизу)`: чем лучше, тем реже.
 */
@Serializable
data class TierGrid(
    val levels: List<Int>,
    val top: List<Range>,
    val bottom: List<Range>,
    val precision: Int = 0,
    val ratio: Double = 0.72,
) {
    fun expand(): List<Tier> {
        val n = levels.size
        return levels.mapIndexed { i, level ->
            val frac = if (n > 1) (n - 1 - i).toDouble() / (n - 1) else 1.0
            val values = top.zip(bottom).map { (t, b) ->
                var lo = round(b[0] + (t[0] - b[0]) * frac, precision)
                var hi = round(b[1] + (t[1] - b[1]) * frac, precision)
                if (hi < lo) lo = hi.also { hi = lo }
                listOf(lo, hi)
            }
            Tier(level, round(1000 * Math.pow(ratio, (n - 1 - i).toDouble()), 0).toInt(), values)
        }
    }

    fun problem(effects: Int): String? = when {
        levels.isEmpty() -> "empty grid"
        top.size != effects || bottom.size != effects -> "$effects effects, ${top.size}/${bottom.size} ranges"
        (top + bottom).any { it.size != 2 } -> "a [min, max] per effect"
        ratio <= 0 -> "ratio $ratio"
        else -> null
    }

    private fun round(value: Double, precision: Int): Double =
        BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_EVEN).toDouble()
}

/** Вариант семейства в файле: без своей сетки наследует тиры семейства. */
@Serializable
data class Variant(
    val kind: VariantKind,
    val grid: TierGrid? = null,
    val tiers: List<Tier> = emptyList(),
    val local: Boolean = false,
    /** Слоты рецепта верстака у CRAFTED; пусто - любой. */
    val slots: List<Slot> = emptyList(),
)

/**
 * Семейство модификаторов (`modifiers.json`): эффекты, тиры сеткой [grid] или явно [tiers], теги и
 * варианты. Из него разворачиваются описания [ModifierDef] - природное и по одному на вариант.
 */
@Serializable
data class ModifierFamily(
    val code: String,
    val source: Source,
    val effects: List<Effect>,
    val grid: TierGrid? = null,
    val tiers: List<Tier> = emptyList(),
    val tags: List<String> = emptyList(),
    val local: Boolean = false,
    /** Группа исключения на одном носителе; null - само семейство. */
    val group: String? = null,
    val influence: Influence? = null,
    val minRarity: MonsterRarity? = null,
    val variants: List<Variant> = emptyList(),
) {
    fun ownTiers(): List<Tier> = grid?.expand() ?: tiers

    fun definitions(): List<ModifierDef> = listOf(definition(VariantKind.NATURAL, ownTiers(), false, emptyList())) +
        variants.map { definition(it.kind, it.grid?.expand() ?: it.tiers.ifEmpty { ownTiers() }, it.local, it.slots) }

    private fun definition(kind: VariantKind, tiers: List<Tier>, variantLocal: Boolean, slots: List<Slot>) = ModifierDef(
        code = VariantKind.code(code, kind),
        family = code,
        variant = kind,
        source = when (kind) {
            VariantKind.IMPLICIT -> Source.IMPLICIT
            VariantKind.CORRUPTED -> Source.CORRUPTION
            VariantKind.ENCHANT -> Source.ENCHANTMENT
            else -> source
        },
        effects = effects,
        tiers = tiers,
        local = local || variantLocal || kind == VariantKind.LOCAL,
        tags = tags + listOfNotNull(kind.suffix?.lowercase()),
        group = group,
        influence = influence,
        crafted = kind == VariantKind.CRAFTED,
        minRarity = minRarity,
        slots = slots,
    )

    fun problem(): String? = when {
        code.isBlank() -> "blank family code"
        code.contains(VariantKind.SEPARATOR) -> "$code: a family code carries no variant separator"
        effects.isEmpty() -> "$code: no effects"
        grid == null && tiers.isEmpty() && source.tiered -> "$code: no tiers"
        source == Source.MONSTER && minRarity == null -> "$code: a monster modifier needs minRarity"
        source != Source.MONSTER && minRarity != null -> "$code: minRarity on a non-monster modifier"
        variants.map { it.kind }.let { it.toSet().size != it.size || VariantKind.NATURAL in it } -> "$code: variants"
        variants.any { (it.kind == VariantKind.LOCAL || it.kind == VariantKind.CRAFTED) && !source.affix } -> "$code: a local or crafted variant of a non-affix"
        else -> (listOfNotNull(grid?.problem(effects.size)) + ownTiers().mapNotNull { it.problem(effects.size) } +
            variants.flatMap { v -> listOfNotNull(v.grid?.problem(effects.size)) + v.tiers.mapNotNull { it.problem(effects.size) } })
            .firstOrNull()?.let { "$code: $it" }
    }
}

/** Источники, чьи описания обязаны иметь тиры: всё, что катается или роллит значения. */
private val Source.tiered: Boolean get() = this != Source.PASSIVE && this != Source.ATLAS && this != Source.RULE

@Serializable
data class ModifiersFile(val families: List<ModifierFamily> = emptyList())

/**
 * Описание модификатора - развёрнутый вариант семейства. Носители ссылаются на него по [code].
 */
data class ModifierDef(
    val code: String,
    val family: String,
    val variant: VariantKind,
    val source: Source,
    val effects: List<Effect>,
    val tiers: List<Tier>,
    val local: Boolean,
    val tags: List<String>,
    val group: String?,
    val influence: Influence?,
    val crafted: Boolean,
    val minRarity: MonsterRarity?,
    val slots: List<Slot> = emptyList(),
) {
    /** Группа исключения: два описания одной группы на одном носителе не встают. */
    val groupKey: String get() = group ?: family
    val affix: Boolean get() = source.affix
    val monster: Boolean get() = source == Source.MONSTER
    val rolls: Boolean get() = tiers.isNotEmpty()

    fun tier(number: Int): Tier? = tiers.getOrNull(number - 1)

    /** Лучший тир, открытый на [level] (номер, тир); на уровне ниже всех - самый слабый. */
    fun bestTierAt(level: Int): Pair<Int, Tier>? {
        val open = tiers.withIndex().filter { it.value.level <= level }.maxByOrNull { it.value.level }
            ?: tiers.withIndex().minByOrNull { it.value.level } ?: return null
        return open.index + 1 to open.value
    }

    fun stats(): List<String> = effects.map { it.stat }
}

/** Одна десятая - точность всех значений ролла. */
fun tenths(value: Double): Double = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()

/** Закреплённая строка носителя: код описания и его значения по эффекту. */
@Serializable
data class Line(val code: String, val values: List<Double> = emptyList())

/**
 * Лестница тиров одного описания: по порогу уровня с накопленными весами - ролл тира двумя двоичными
 * поисками. Тир без веса весит свой номер.
 */
class TierLadder(tiers: List<Tier>) {
    private val byLevel: List<Pair<Int, Tier>> = tiers.mapIndexed { index, tier -> index + 1 to tier }.sortedBy { it.second.level }
    private val levels = IntArray(byLevel.size) { byLevel[it].second.level }
    private val cumulative = LongArray(byLevel.size).also { sums ->
        var total = 0L
        byLevel.forEachIndexed { index, (number, tier) -> total += (tier.weight.takeIf { it > 0 } ?: number).toLong(); sums[index] = total }
    }

    /** Открытых тиров на уровне [level]: первая позиция с порогом выше уровня. */
    fun openCount(level: Int): Int {
        var low = 0
        var high = levels.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (levels[middle] <= level) low = middle + 1 else high = middle
        }
        return low
    }

    /** Тир по точке [point] в `[0, total(level))`; ни одного открытого - самый слабый. */
    fun pick(level: Int, point: (Long) -> Long): Pair<Int, Tier>? {
        if (byLevel.isEmpty()) return null
        val open = openCount(level)
        if (open == 0) return byLevel.maxBy { it.first }
        val target = point(cumulative[open - 1])
        var low = 0
        var high = open - 1
        while (low < high) {
            val middle = (low + high) ushr 1
            if (cumulative[middle] > target) high = middle else low = middle + 1
        }
        return byLevel[low]
    }
}
