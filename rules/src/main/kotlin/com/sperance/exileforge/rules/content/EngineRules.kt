package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/**
 * Сферы: связка предмета сумки категории CURRENCY с его поведением - у каждой своя ветка правил,
 * поэтому чисто данными она быть не может. Код предмета равен имени сферы.
 */
@Serializable
enum class Orb {
    ORB_OF_TRANSMUTATION, ORB_OF_AUGMENTATION, ORB_OF_ALTERATION, ORB_OF_ALCHEMY, REGAL_ORB, CHAOS_ORB, EXALTED_ORB, DIVINE_ORB,
    ORB_OF_ANNULMENT, ORB_OF_SCOURING, BLESSED_ORB, VAAL_ORB, ORB_OF_CHANCE, MIRROR_OF_KALANDRA, FRACTURING_ORB,
    SHAPERS_ORB, ELDER_ORB, ABYSS_ORB, ORB_OF_REGRET,
    /** 1.35.0: сфера раскрытия скрытого модификатора; 1.65.0: сфера качества - база, фляга, инструмент, карта, питомец, с катализатором - вид. */
    UNVEILING_ORB, QUALITY_ORB;

    val influence: Influence? get() = when (this) { SHAPERS_ORB -> Influence.SHAPER; ELDER_ORB -> Influence.ELDER; ABYSS_ORB -> Influence.ABYSS; else -> null }

    companion object {
        private val byName = entries.associateBy { it.name }
        fun of(code: String): Orb? = byName[code]
    }
}

/** Витрина торговца: окно, число вещей, разброс уровня, наценка, таблицы товара и весов редкости. */
@Serializable
data class MerchantRules(
    val windowHours: Double = 4.0, val minOffers: Int = 12, val maxOffers: Int = 16, val levelSpread: Int = 2, val markup: Int = 3,
    val tables: List<String> = listOf("merchant"), val rarities: String = "rarity:merchant", val flasks: List<Int> = listOf(1, 2),
    val orbs: OrbShelf = OrbShelf(),
)

/**
 * Полка сфер торговца (1.13.0): сферы [codes] за золото, цена - цена сферы с наценкой [markup], и каждая
 * покупка того же вида за окно дороже в [growth] раз; новое окно сбрасывает рост. Запас на окно [stock]
 * (1.22.0): сферы, которой нет в нём, не больше [defaultStock].
 */
@Serializable
data class OrbShelf(
    val codes: List<String> = emptyList(),
    val markup: Double = 2.0,
    val growth: Double = 1.25,
    val stock: Map<String, Int> = emptyMap(),
    val defaultStock: Int = 3,
) {
    fun stockOf(code: String): Int = stock[code] ?: defaultStock

    fun price(orbPrice: Long, bought: Int): Long = Math.round(orbPrice * markup * Math.pow(growth, bought.toDouble()))
}

/**
 * Цена торговца (1.22.0): база [base] шаблона без своей цены растёт с его уровнем, как золото с монстров;
 * доля базы за аффикс, множитель редкости и качество роллов - [qualityFloor] + средняя доля роллов 0..1
 * (вещь без роллов считается средней, [neutralQuality]).
 */
@Serializable
data class SellRules(
    val base: Double = 10.0,
    val affixShare: Double = 0.15,
    val qualityFloor: Double = 0.5,
    val neutralQuality: Double = 0.5,
    val rarity: Map<Rarity, Double> = mapOf(Rarity.COMMON to 1.0, Rarity.MAGIC to 1.5, Rarity.RARE to 2.5, Rarity.UNIQUE to 8.0, Rarity.MYTHICAL to 20.0),
)

@Serializable data class BenchCost(val orb: Orb, val amount: Long)

/** Верстак: цена каждого тира рецепта (индекс - тир минус один), сфера снятия, потолок уровня карт для тира рецепта. */
@Serializable
data class BenchRules(
    val costs: List<BenchCost> = emptyList(),
    val uncraftOrb: Orb = Orb.ORB_OF_SCOURING,
    val maxMapLevel: Int = 20,
) {
    val maxTier: Int get() = costs.size

    /** Тир рецепта, который может выпасть на локации [level]: чем ниже уровень, тем выше тир. */
    fun tierFor(level: Int): Int = (maxTier - (level - 1) * maxTier / maxMapLevel).coerceIn(1, maxTier)
}

/**
 * Сферы: шанс уникалки у Orb of Chance и её таблицы, минимум аффиксов для закрепления, сдвиг Ваал, потолок строк алхимии карты
 * и таблица этих строк [mapAlchemy] (1.65.0: их кладёт сфера алхимии на карту), сколько вариантов даёт знамение выбора [choices].
 */
@Serializable
data class OrbRules(
    val chanceUniquePercent: Double = 5.0,
    val chanceRarities: String = "rarity:chance",
    val chanceUniques: List<String> = listOf("unique:chance"),
    val fractureMinAffixes: Int = 4,
    val vaalShift: List<Double> = listOf(0.8, 1.2),
    val maxAlchemyLines: Int = 3,
    val mapAlchemy: String = "alchemy:map",
    val choices: Int = 3,
)

/** Фляги: стартовая, потолок качества, сферы, что фляга принимает (качество - сферой качества по шагу [QualityRules]). */
@Serializable
data class FlaskRules(
    val starter: String = "FLASK_SMALL_LIFE",
    val maxQuality: Int = 20,
    val orbs: List<Orb> = listOf(Orb.ORB_OF_TRANSMUTATION, Orb.ORB_OF_ALTERATION, Orb.ORB_OF_AUGMENTATION, Orb.ORB_OF_SCOURING, Orb.ORB_OF_CHANCE, Orb.BLESSED_ORB, Orb.DIVINE_ORB, Orb.VAAL_ORB, Orb.QUALITY_ORB),
)

/**
 * Стартовый набор героя (1.12.0): золото на первые покупки у торговца и инструменты по префиксу; оружие и броня -
 * у класса, фляга - в правилах фляг. Сфер и случайных вещей нет: их находят в первой же зоне.
 */
@Serializable
data class StarterRules(val gold: Long = 0, val toolPrefix: String = "BRONZE_")

/** Аукцион; [lotDays] - сколько дней лот стоит на витрине (1.30.0): потом товар возвращается продавцу, сбор не возвращается. */
@Serializable
data class AuctionRules(
    /** Мест под лоты у каждого героя (1.44.0): поровну и без докупки. */
    val slots: Int = 12, val minLevel: Int = 1,
    val buyerFee: Double = 0.0, val lotDays: Int = 30,
    /** Валюта аукциона (1.65.0): цена лота, покупка и фильтр витрины - только этими базовыми сферами; товаром идёт любой предмет. */
    val currencies: List<Orb> = BASE_CURRENCIES,
) {
    /** Можно ли назначить цену в предмете [code]. */
    fun trades(code: String): Boolean = currencies.any { it.name == code }

    /** Сбор с покупателя золотом (1.13.0): [buyerFee] процентов цены лота в ценах сфер [orbPrice]; в дробях - без переполнения Long. */
    fun fee(orbPrice: Long, price: Long): Long = Math.round(orbPrice.toDouble() * price.toDouble() * buyerFee / 100).coerceAtLeast(0)

    /** Срок лота в миллисекундах. */
    val lotMillis: Long get() = lotDays * 86_400_000L

    companion object {
        val BASE_CURRENCIES = listOf(
            Orb.ORB_OF_TRANSMUTATION, Orb.ORB_OF_AUGMENTATION, Orb.ORB_OF_ALTERATION, Orb.ORB_OF_ALCHEMY, Orb.REGAL_ORB, Orb.CHAOS_ORB, Orb.EXALTED_ORB,
            Orb.DIVINE_ORB, Orb.ORB_OF_ANNULMENT, Orb.ORB_OF_SCOURING, Orb.BLESSED_ORB, Orb.ORB_OF_CHANCE,
        )
    }
}

/**
 * Тайник героя (1.1.0): сколько копий вещей он держит - база и докупленные за золото пачки мест, но
 * не больше [maxSlots] и никогда не больше [HARD_CAP], считая надетое. Что не влезло, ждёт в
 * переполнении до [overflowSlots] копий; сверх него вещь продаётся торговцу сама.
 */
@Serializable
data class StashRules(
    val baseSlots: Int = 200,
    val maxSlots: Int = HARD_CAP,
    val slotStep: Int = 50,
    val firstPrice: Double = 1000.0,
    val priceGrowth: Double = 1.25,
    val overflowSlots: Int = 100,
) {
    /** Мест у героя, докупившего [bought] пачек. */
    fun capacity(bought: Int): Int = (baseSlots + bought * slotStep).coerceAtMost(maxSlots)

    /** Цена следующей пачки; ноль - докупать нечего. */
    fun price(bought: Int): Long = if (capacity(bought) >= maxSlots) 0 else Math.round(firstPrice * Math.pow(priceGrowth, bought.toDouble()))

    fun validate() {
        if (baseSlots < 1 || maxSlots !in baseSlots..HARD_CAP || slotStep < 1 || firstPrice < 0 || priceGrowth < 1 || overflowSlots < 0) fail("rules: stash")
    }

    companion object {
        /** Потолок копий на героя при любых правилах: документ героя не растёт без меры. */
        const val HARD_CAP = 1000
    }
}

/**
 * Разброс базы вещи: копия катит качество базы [min]..[max] процентов и получает [perLevel] процента за каждый уровень
 * предмета сверх уровня шаблона, не больше [cap]. Множитель ложится только на строки базы [lines] - броню, уклонение,
 * энерощит и урон оружия; блок, крит, скорость и требования шаблона не меняются.
 */
@Serializable
data class BaseVariance(
    val min: Int = 90,
    val max: Int = 110,
    val perLevel: Double = 1.0,
    val cap: Double = 20.0,
    val lines: Set<String> = setOf("BASE_ARMOUR", "BASE_EVASION", "BASE_ENERGY_SHIELD", "BASE_PHYSICAL_DAMAGE"),
) {
    /** Множитель базы копии шаблона [template] с качеством базы [quality] (0 - как 100) и уровнем предмета [itemLevel] (0 - уровень шаблона). */
    fun scale(template: ItemTemplate, quality: Int, itemLevel: Int): Double {
        val rolled = if (quality > 0) quality else NEUTRAL
        val above = if (itemLevel > 0) (itemLevel - template.level).coerceAtLeast(0) else 0
        return rolled / 100.0 * (1 + (perLevel * above).coerceAtMost(cap) / 100.0)
    }

    /** Строки базы шаблона под множителем [scale]: значения округлены до целого, ненулевое не падает ниже единицы. */
    fun base(template: ItemTemplate, scale: Double): List<Line> =
        if (scale == 1.0) template.base
        else template.base.map { line ->
            if (line.code !in lines) line
            else line.copy(values = line.values.map { value -> if (value == 0.0) value else Math.round(value * scale).toDouble().coerceAtLeast(1.0) })
        }

    fun validate() {
        if (min !in 1..max || perLevel < 0 || cap < 0) fail("loot: baseVariance")
    }

    companion object {
        /** Качество базы копии до разброса. */
        const val NEUTRAL = 100
    }
}

/**
 * Добыча: рост золота с уровнем и его спад [goldTaper] (1.13.0; нет - спад роста монстров), степень опыта,
 * веса редкости шаблона в тяге, дальность уникалок боссов, шанс рецепта.
 */
@Serializable
data class LootRules(
    val goldGrowth: Double = 1.1,
    val goldTaper: GrowthTaper? = null,
    val experienceWindow: ExperienceWindow = ExperienceWindow(),
    val experiencePower: Double = 1.9,
    val rarityWeights: Map<Rarity, Double> = mapOf(Rarity.COMMON to 100.0, Rarity.MAGIC to 40.0, Rarity.RARE to 15.0, Rarity.UNIQUE to 1.0, Rarity.MYTHICAL to 0.2),
    val uniqueReach: Int = 10,
    val recipeChance: Double = 0.10,
    /** Уровень предмета (ilvl, 1.33.0): потолок (1.40.0 - 100) и прибавка к уровню зоны от редкости убитого монстра. */
    val maxItemLevel: Int = 100,
    val itemLevelBonus: Map<MonsterRarity, Int> = mapOf(MonsterRarity.MAGIC to 1, MonsterRarity.RARE to 2, MonsterRarity.UNIQUE to 3),
    /** Прибавка к ilvl вещи ремесла на предельном уровне профессии; на промежуточных - по доле уровня. */
    val craftItemLevelBonus: Int = 10,
    /** С какого уровня героя ему выпадают самоцветы - из любого источника: добыча, награды, торговец, ремесло, коды. */
    val jewelHeroLevel: Int = 5,
    /** Разброс базы (1.67.0): качество базы копии и прибавка за уровень предмета сверх уровня шаблона. */
    val baseVariance: BaseVariance = BaseVariance(),
) {
    /** Может ли герой уровня [heroLevel] получить новую вещь шаблона [template]: самоцвет - не раньше [jewelHeroLevel]. */
    fun obtainable(template: ItemTemplate, heroLevel: Int): Boolean = template.slot != Slot.JEWEL || heroLevel >= jewelHeroLevel

    /** Уровень выпавшей копии: зона [level], редкость источника [rarity] и прибавка карты [extra], не выше потолка. */
    fun itemLevel(level: Int, rarity: MonsterRarity, extra: Int = 0): Int =
        (level + (itemLevelBonus[rarity] ?: 0) + extra).coerceIn(1, maxItemLevel)

    /** Уровень копии, созданной не добычей (торговец, ремесло, награда): уровень героя [heroLevel]. */
    fun itemLevel(heroLevel: Int): Int = heroLevel.coerceIn(1, maxItemLevel)

    /** Уровень вещи ремесла: уровень героя и доля уровня профессии [craftLevel] из [craftMax]. */
    fun craftedItemLevel(heroLevel: Int, craftLevel: Int, craftMax: Int): Int =
        itemLevel(heroLevel + craftItemLevelBonus * craftLevel.coerceIn(0, craftMax) / craftMax.coerceAtLeast(1))

    /** Во сколько раз золото на уровне [level] больше, чем на первом: рост со своим спадом или спадом роста монстров [fallback]. */
    fun goldScale(level: Int, fallback: GrowthTaper): Double = Math.pow(goldGrowth, (goldTaper ?: fallback).steps(level))
}

/**
 * Штраф опыта за разницу уровней героя и зоны (1.16.0), как в PoE: окно [base] + уровень / [perLevel] без
 * штрафа, сверх него доля `((герой + 5) / (герой + 5 + разница^[power]))^[outer]`, не ниже [floor].
 */
@Serializable
data class ExperienceWindow(val base: Double = 3.0, val perLevel: Double = 16.0, val power: Double = 2.5, val outer: Double = 1.5, val floor: Double = 0.01) {
    fun share(heroLevel: Int, zoneLevel: Int): Double {
        val excess = Math.abs(heroLevel - zoneLevel) - (base + heroLevel / perLevel).toInt()
        if (excess <= 0) return 1.0
        val hero = heroLevel + 5.0
        return Math.pow(hero / (hero + Math.pow(excess.toDouble(), power)), outer).coerceAtLeast(floor)
    }
}

/** Заход: бросить открытый заход ради нового семени - не чаще [newSeedSeconds]; вход без карты в ту же зону продолжает прежний. */
@Serializable
data class RunRules(val newSeedSeconds: Int = 30)

/** Зверинец (1.5.0): сколько питомцев держит герой и сколько золота даёт отпущенный за уровень по редкости. */
@Serializable
data class PetRules(val cap: Int = 20, val releaseGold: Map<Rarity, Long> = mapOf(Rarity.COMMON to 20L, Rarity.MAGIC to 60L, Rarity.RARE to 200L)) {
    fun releasePrice(rarity: Rarity, level: Int): Long = (releaseGold[rarity] ?: 0L) * level.coerceAtLeast(1)
}

/**
 * Заряды героя (1.32.0): ярость, сила, выносливость. Максимум вида - [maximum] + стат [ChargeRule.max] листа (не меньше нуля), срок -
 * [duration] с × (1 + [durationStat]/100); получение заряда вида обновляет срок всех его зарядов, конец боя их снимает, этап поэтапного
 * боя передаёт следующему. Каждый заряд кладёт на героя строки [ChargeRule.lines]. Получают заряды только силы и умения.
 */
@Serializable
data class ChargeRules(
    val maximum: Int = 3,
    val duration: Double = 10.0,
    val durationStat: String = "STOCK_CHARGE_DURATION",
    val kinds: Map<ChargeKind, ChargeRule> = mapOf(
        ChargeKind.FRENZY to ChargeRule(
            "STOCK_MAX_FRENZY_CHARGES",
            listOf(PowerLine("STOCK_ATTACK_SPEED", Op.INCREASED, 2.0), PowerLine("STOCK_CAST_SPEED", Op.INCREASED, 2.0), PowerLine("STOCK_DAMAGE", Op.MORE, 2.0)),
        ),
        ChargeKind.POWER to ChargeRule(
            "STOCK_MAX_POWER_CHARGES", listOf(PowerLine("STOCK_CRITICAL_CHANCE", Op.INCREASED, 20.0), PowerLine("STOCK_SPELL_CRITICAL_CHANCE", Op.INCREASED, 20.0)),
        ),
        ChargeKind.ENDURANCE to ChargeRule(
            "STOCK_MAX_ENDURANCE_CHARGES", listOf(PowerLine("STOCK_PHYSICAL_REDUCTION", Op.ADD, 2.0), PowerLine("STOCK_RESIST_ALL", Op.ADD, 2.0)),
        ),
    ),
) {
    /** Сколько зарядов вида [kind] держит герой с листом [sheet]. */
    fun max(kind: ChargeKind, sheet: Map<String, Double>): Int =
        kinds[kind]?.let { (maximum + (sheet[it.max] ?: 0.0).toInt()).coerceAtLeast(0) } ?: 0

    /** Срок заряда у героя с листом [sheet], секунды. */
    fun lifetime(sheet: Map<String, Double>): Double = (duration * (1 + (sheet[durationStat] ?: 0.0) / 100)).coerceAtLeast(0.0)

    fun validate(stats: StatRegistry) {
        if (maximum < 0 || duration <= 0 || durationStat !in stats) fail("rules: charges")
        if (kinds.keys != ChargeKind.REAL.toSet()) fail("rules: charges of ${kinds.keys}, need ${ChargeKind.REAL}")
        kinds.forEach { (kind, rule) ->
            if (rule.max !in stats || rule.lines.isEmpty() || rule.lines.any { it.stat !in stats || it.value == null || it.scale != null }) fail("rules: charge $kind")
        }
    }
}

/** Вид заряда: стат прибавки к максимуму и строки одного заряда (значение в строке обязательно). */
@Serializable
data class ChargeRule(val max: String, val lines: List<PowerLine>)

/**
 * Правила движка (`rules.json`): всё, что раньше было константами кода, - места аффиксов редкостей,
 * торговец, цена, верстак, сферы, фляги, стартовый набор, аукцион, добыча, тайник, заход.
 */
@Serializable
data class EngineRules(
    val rarities: Map<String, RarityLimits> = mapOf(
        "COMMON" to RarityLimits(), "MAGIC" to RarityLimits(1, 1, listOf(1, 2)), "RARE" to RarityLimits(3, 3, listOf(4, 6)),
        "RARE:JEWEL" to RarityLimits(2, 2, listOf(3, 4)), "UNIQUE" to RarityLimits(), "MYTHICAL" to RarityLimits(),
    ),
    val merchant: MerchantRules = MerchantRules(),
    val sell: SellRules = SellRules(),
    val bench: BenchRules = BenchRules(),
    val orbs: OrbRules = OrbRules(),
    val flasks: FlaskRules = FlaskRules(),
    val starter: StarterRules = StarterRules(),
    val auction: AuctionRules = AuctionRules(),
    val loot: LootRules = LootRules(),
    val stash: StashRules = StashRules(),
    val run: RunRules = RunRules(),
    val pets: PetRules = PetRules(),
    val charges: ChargeRules = ChargeRules(),
    val quality: QualityRules = QualityRules(),
    val maxCharacters: Int = 3,
    /**
     * Снятые предметы сумки (1.65.0): старый код → код, которым он становится в сумке героя при чтении документа. Так сферы
     * питомцев, сферы карт и качества прежних версий не остаются в сумке мёртвым грузом.
     */
    val retired: Map<String, String> = emptyMap(),
) {
    /** Места аффиксов редкости на предмете слота: `<редкость>:<слот>` перекрывает `<редкость>`. */
    fun limits(rarity: Rarity, slot: Slot? = null): RarityLimits =
        slot?.let { rarities["${rarity.name}:${it.name}"] } ?: rarities[rarity.name] ?: RarityLimits()

    fun validate() {
        Rarity.entries.forEach { if (it.name !in rarities) fail("rules: rarity ${it.name} without limits") }
        rarities.forEach { (key, limits) ->
            if (limits.prefixes < 0 || limits.suffixes < 0 || limits.affixes.size != 2 || limits.affixes[0] > limits.affixes[1] || limits.affixes[1] > limits.prefixes + limits.suffixes) fail("rules: limits $key")
        }
        if (bench.costs.isEmpty() || bench.maxMapLevel < 1) fail("rules: bench")
        if (merchant.minOffers < 0 || merchant.minOffers > merchant.maxOffers || merchant.markup < 1 || merchant.windowHours <= 0) fail("rules: merchant")
        if (merchant.orbs.markup < 1 || merchant.orbs.growth < 1) fail("rules: merchant orbs")
        if (orbs.vaalShift.size != 2 || orbs.vaalShift[0] > orbs.vaalShift[1] || orbs.fractureMinAffixes < 1 || orbs.maxAlchemyLines < 0 || orbs.choices < 1) fail("rules: orbs")
        if (loot.rarityWeights.keys != Rarity.entries.toSet()) fail("rules: loot rarity weights")
        loot.baseVariance.validate()
        if (auction.slots < 1 || auction.buyerFee < 0 || auction.currencies.isEmpty()) fail("rules: auction")
        stash.validate()
        if (run.newSeedSeconds < 0) fail("rules: run")
        if (auction.lotDays <= 0) fail("rules: auction.lotDays")
        if (pets.cap < 1 || pets.releaseGold.values.any { it < 0 }) fail("rules: pets")
    }
}
