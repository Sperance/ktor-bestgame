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
    EMPOWERING_ORB, MERCY_ORB, PERIL_ORB, HORDE_ORB, MAGUS_ORB, ELITE_ORB, BOUNTY_ORB, TREASURE_ORB, GILDED_ORB, WARDEN_ORB, ESSENCE_ORB, SCRIBE_ORB,
    GLASSBLOWERS_BAUBLE,
    HELMET_SCROLL, GLOVES_SCROLL, BOOTS_SCROLL, WEAPON_SCROLL;

    val mapOnly: Boolean get() = ordinal >= EMPOWERING_ORB.ordinal && ordinal <= SCRIBE_ORB.ordinal
    val flaskOnly: Boolean get() = this == GLASSBLOWERS_BAUBLE
    val enchantSlot: String? get() = when (this) { HELMET_SCROLL -> "helmet"; GLOVES_SCROLL -> "gloves"; BOOTS_SCROLL -> "boots"; WEAPON_SCROLL -> "weapon"; else -> null }
    val influence: Influence? get() = when (this) { SHAPERS_ORB -> Influence.SHAPER; ELDER_ORB -> Influence.ELDER; ABYSS_ORB -> Influence.ABYSS; else -> null }
    /** Строка «Алхимия», которую кладёт сфера алхимика; null у прочих. */
    val alchemyLine: String? get() = when (this) {
        HORDE_ORB -> "ALC_MAP_PACK"; MAGUS_ORB -> "ALC_MAP_MAGIC"; ELITE_ORB -> "ALC_MAP_RARE"; BOUNTY_ORB -> "ALC_MAP_LOOT"; TREASURE_ORB -> "ALC_MAP_CHESTS"
        GILDED_ORB -> "ALC_MAP_GOLD"; WARDEN_ORB -> "ALC_MAP_BOSS"; ESSENCE_ORB -> "ALC_MAP_CRYSTALS"; SCRIBE_ORB -> "ALC_MAP_BOOKS"; else -> null
    }

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
)

/** Цена торговца: доля базы за аффикс и множитель редкости. */
@Serializable
data class SellRules(val affixShare: Double = 0.15, val rarity: Map<Rarity, Double> = mapOf(Rarity.COMMON to 1.0, Rarity.UNCOMMON to 1.5, Rarity.RARE to 2.5, Rarity.UNIQUE to 8.0, Rarity.MYTHICAL to 20.0))

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

/** Сферы: шанс уникалки у Orb of Chance и её таблицы, минимум аффиксов для закрепления, сдвиг Ваал, потолок строк алхимии. */
@Serializable
data class OrbRules(
    val chanceUniquePercent: Double = 5.0,
    val chanceRarities: String = "rarity:chance",
    val chanceUniques: List<String> = listOf("unique:chance"),
    val fractureMinAffixes: Int = 4,
    val vaalShift: List<Double> = listOf(0.8, 1.2),
    val maxAlchemyLines: Int = 3,
)

/** Фляги: стартовая, потолок качества, шаги «Стеклодува», сферы, что фляга принимает. */
@Serializable
data class FlaskRules(
    val starter: String = "FLASK_SMALL_LIFE",
    val maxQuality: Int = 20,
    val baubleCommon: Int = 2,
    val baubleMagic: Int = 1,
    val orbs: List<Orb> = listOf(Orb.ORB_OF_TRANSMUTATION, Orb.ORB_OF_ALTERATION, Orb.ORB_OF_AUGMENTATION, Orb.ORB_OF_SCOURING, Orb.ORB_OF_CHANCE, Orb.BLESSED_ORB, Orb.DIVINE_ORB, Orb.VAAL_ORB, Orb.GLASSBLOWERS_BAUBLE),
)

/** Стартовый набор героя: сферы, инструменты по префиксу, вещи по редкостям, фляга. */
@Serializable
data class StarterRules(val orbs: Long = 20, val toolPrefix: String = "BRONZE_", val gear: List<Rarity> = listOf(Rarity.COMMON, Rarity.UNIQUE))

@Serializable
data class AuctionRules(val baseSlots: Int = 5, val maxSlots: Int = 20, val firstSlotPrice: Double = 500.0, val slotGrowth: Double = 1.5, val minLevel: Int = 1) {
    fun slotPrice(bought: Int): Long = Math.round(firstSlotPrice * Math.pow(slotGrowth, bought.toDouble()))
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

/** Добыча: рост золота с уровнем, степень опыта, веса редкости шаблона в тяге, дальность уникалок боссов, шанс рецепта. */
@Serializable
data class LootRules(
    val goldGrowth: Double = 1.1,
    val experiencePower: Double = 1.9,
    val rarityWeights: Map<Rarity, Double> = mapOf(Rarity.COMMON to 100.0, Rarity.UNCOMMON to 40.0, Rarity.RARE to 15.0, Rarity.UNIQUE to 1.0, Rarity.MYTHICAL to 0.2),
    val uniqueReach: Int = 10,
    val recipeChance: Double = 0.10,
)

/** Заход: бросить открытый заход ради нового семени - не чаще [newSeedSeconds]; вход без карты в ту же зону продолжает прежний. */
@Serializable
data class RunRules(val newSeedSeconds: Int = 30)

/** Зверинец (1.5.0): сколько питомцев держит герой и сколько золота даёт отпущенный за уровень по редкости. */
@Serializable
data class PetRules(val cap: Int = 20, val releaseGold: Map<Rarity, Long> = mapOf(Rarity.COMMON to 20L, Rarity.UNCOMMON to 60L, Rarity.RARE to 200L)) {
    fun releasePrice(rarity: Rarity, level: Int): Long = (releaseGold[rarity] ?: 0L) * level.coerceAtLeast(1)
}

/**
 * Правила движка (`rules.json`): всё, что раньше было константами кода, - места аффиксов редкостей,
 * торговец, цена, верстак, сферы, фляги, стартовый набор, аукцион, добыча, тайник, заход.
 */
@Serializable
data class EngineRules(
    val rarities: Map<String, RarityLimits> = mapOf(
        "COMMON" to RarityLimits(), "UNCOMMON" to RarityLimits(1, 1, listOf(1, 2)), "RARE" to RarityLimits(3, 3, listOf(4, 6)),
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
    val maxCharacters: Int = 3,
    val maxStack: Long = 100_000_000_000L,
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
        if (orbs.vaalShift.size != 2 || orbs.vaalShift[0] > orbs.vaalShift[1] || orbs.fractureMinAffixes < 1 || orbs.maxAlchemyLines < 0) fail("rules: orbs")
        if (loot.rarityWeights.keys != Rarity.entries.toSet()) fail("rules: loot rarity weights")
        if (auction.baseSlots < 0 || auction.maxSlots < auction.baseSlots) fail("rules: auction")
        stash.validate()
        if (run.newSeedSeconds < 0) fail("rules: run")
        if (pets.cap < 1 || pets.releaseGold.values.any { it < 0 }) fail("rules: pets")
    }
}
