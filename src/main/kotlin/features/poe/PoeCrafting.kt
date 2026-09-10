package features.poe

import kotlinx.serialization.json.*
import kotlin.random.Random

/** Pure engine: all changes are computed before any inventory mutation or currency debit. */
class PoeCrafting(private val catalog: PoeCatalog, private val random: Random = Random.Default) {
    private val affixes = catalog.mods.filterValues { it.string("domain") == "item" && it.string("generation_type") in setOf("prefix", "suffix") }

    fun roll(id: String): PoeRoll = PoeRoll(id, catalog.mod(id).objects("stats").map {
        random.nextLong(it.int("min").toLong(), it.int("max").toLong() + 1).toInt()
    })

    fun generate(baseId: String, itemLevel: Int, rarity: PoeRarity): PoeItem {
        val base = catalog.base(baseId)
        require(catalog.wearable(base) && base.string("release_state") == "released") { "Base is not released wearable equipment" }
        require(itemLevel in base.int("drop_level", 1).coerceAtLeast(1)..100) { "Invalid drop item level" }
        require(rarity != PoeRarity.UNIQUE) { "Unique items require a curated unique definition, not random affixes" }
        val item = PoeItem(baseId, itemLevel, rarity, base.strings("implicits").map(::roll),
            corrupted = baseId.contains("/Talismans/"))
        return when (rarity) {
            PoeRarity.MAGIC -> fill(item, random.nextInt(1, 3))
            PoeRarity.RARE -> fill(item, rareCount())
            else -> item
        }
    }

    // Server balance weights, explicitly not claimed to reproduce undisclosed GGG probabilities.
    private fun rareCount(): Int = when (random.nextInt(12)) { in 0..7 -> 4; in 8..10 -> 5; else -> 6 }
    private fun kind(roll: PoeRoll) = catalog.mod(roll.id).string("generation_type")
    private fun stat(item: PoeItem, id: String) = item.explicits.any { r ->
        catalog.mod(r.id).objects("stats").withIndex().any { (index, s) -> s.string("id") == id && r.values.getOrElse(index) { 0 } != 0 }
    }
    private fun protected(item: PoeItem, roll: PoeRoll): Boolean = roll.fractured ||
        (kind(roll) == "prefix" && stat(item, "item_generation_cannot_change_prefixes")) ||
        (kind(roll) == "suffix" && stat(item, "item_generation_cannot_change_suffixes"))

    /** First matching spawn weight wins, including a zero before a later positive default. */
    private fun weight(definition: JsonObject, tags: Set<String>): Double {
        val spawn = definition.objects("spawn_weights").firstOrNull { it.string("tag") in tags }?.int("weight") ?: 0
        val generation = definition.objects("generation_weights").firstOrNull { it.string("tag") in tags }?.int("weight", 100) ?: 100
        return spawn.coerceAtLeast(0).toDouble() * generation.coerceAtLeast(0) / 100.0
    }

    fun eligible(item: PoeItem): Map<String, Double> {
        val base = catalog.base(item.baseId)
        val tags = base.strings("tags").toMutableSet()
        (item.implicits + item.explicits).forEach { tags += catalog.mod(it.id).strings("adds_tags") }
        // Influenced state is rejected by validate until class-specific influence tags are implemented.
        val groups = item.explicits.flatMap { catalog.mod(it.id).strings("groups") }.toSet()
        val cap = if (item.rarity == PoeRarity.MAGIC) 1 else 3
        val counts = item.explicits.groupingBy(::kind).eachCount()
        val noAttack = stat(item, "item_generation_cannot_roll_attack_affixes")
        val noCaster = stat(item, "item_generation_cannot_roll_caster_affixes")
        return affixes.mapNotNull { (id, d) ->
            if (d.int("required_level") > item.itemLevel || d["is_essence_only"]?.jsonPrimitive?.booleanOrNull == true ||
                d.strings("groups").any { it in groups } || item.explicits.any { it.id == id } ||
                (counts[d.string("generation_type")] ?: 0) >= cap ||
                (noAttack && "attack" in d.strings("implicit_tags")) || (noCaster && "caster" in d.strings("implicit_tags"))) return@mapNotNull null
            val w = weight(d, tags)
            if (w > 0) id to w else null
        }.toMap()
    }
    private fun add(item: PoeItem): PoeItem {
        val max = if (item.rarity == PoeRarity.MAGIC) 2 else 6
        require(item.explicits.size < max) { "No free explicit modifier slot" }
        val choices = eligible(item)
        require(choices.isNotEmpty()) { "No eligible modifier; currency was not consumed" }
        var ticket = random.nextDouble(choices.values.sum())
        val id = choices.entries.firstOrNull { ticket -= it.value; ticket < 0 }?.key ?: choices.keys.last()
        return item.copy(explicits = item.explicits + roll(id))
    }
    private fun fill(item: PoeItem, target: Int): PoeItem {
        var result = item
        repeat((target - item.explicits.size).coerceAtLeast(0)) { result = add(result) }
        return result
    }
    fun validate(item: PoeItem) {
        require(catalog.wearable(catalog.base(item.baseId))) { "Unsupported item class" }
        require(item.itemLevel in 1..100 && item.quality in 0..30) { "Invalid item state" }
        require(item.catalogVersion == "3.29.3.3") { "Migrate item catalog version before crafting" }
        require(item.influences.isEmpty()) { "Influenced crafting is not enabled in this ruleset" }
        require(item.explicits.all { kind(it) in setOf("prefix", "suffix") }) { "Unsupported explicit generation type" }
        val cap = when(item.rarity) { PoeRarity.NORMAL -> 0; PoeRarity.MAGIC -> 1; else -> 3 }
        require(item.explicits.groupingBy(::kind).eachCount().values.all { it <= cap }) { "Invalid affix count" }
        val groups = item.explicits.flatMap { catalog.mod(it.id).strings("groups") }
        require(groups.size == groups.toSet().size) { "Conflicting modifier groups" }
        (item.implicits + item.explicits).forEach { r ->
            val stats = catalog.mod(r.id).objects("stats")
            require(r.values.size == stats.size && stats.indices.all { r.values[it] in stats[it].int("min")..stats[it].int("max") }) { "Invalid roll: ${r.id}" }
        }
    }
    fun apply(item: PoeItem, currency: PoeCurrency): PoeItem {
        validate(item)
        require(!item.corrupted && !item.mirrored) { "Corrupted and mirrored equipment cannot be modified" }
        require(item.rarity != PoeRarity.UNIQUE) { "Unique crafting requires curated unique definitions" }
        fun rarity(vararg allowed: PoeRarity) = require(item.rarity in allowed) { "${currency.displayName} cannot be used on ${item.rarity}" }
        val kept = item.explicits.filter { protected(item, it) }
        val result = when (currency) {
            PoeCurrency.TRANSMUTATION -> { rarity(PoeRarity.NORMAL); fill(item.copy(rarity = PoeRarity.MAGIC), random.nextInt(1, 3)) }
            PoeCurrency.AUGMENTATION -> { rarity(PoeRarity.MAGIC); add(item) }
            PoeCurrency.ALTERATION -> { rarity(PoeRarity.MAGIC); fill(item.copy(explicits = kept), random.nextInt(1, 3).coerceAtLeast(kept.size)) }
            PoeCurrency.ALCHEMY -> { rarity(PoeRarity.NORMAL); fill(item.copy(rarity = PoeRarity.RARE), rareCount()) }
            PoeCurrency.CHAOS -> { rarity(PoeRarity.RARE); fill(item.copy(explicits = kept), rareCount().coerceAtLeast(kept.size)) }
            PoeCurrency.REGAL -> { rarity(PoeRarity.MAGIC); add(item.copy(rarity = PoeRarity.RARE)) }
            PoeCurrency.EXALTED -> { rarity(PoeRarity.RARE); add(item) }
            PoeCurrency.SCOURING -> {
                rarity(PoeRarity.MAGIC, PoeRarity.RARE)
                item.copy(explicits = kept, rarity = when {
                    kept.isEmpty() -> PoeRarity.NORMAL
                    kept.groupingBy(::kind).eachCount().values.all { it <= 1 } -> PoeRarity.MAGIC
                    else -> PoeRarity.RARE
                })
            }
            PoeCurrency.ANNULMENT -> {
                rarity(PoeRarity.MAGIC, PoeRarity.RARE)
                val removable = item.explicits.filterNot { protected(item, it) }
                require(removable.isNotEmpty()) { "No removable modifiers" }
                item.copy(explicits = item.explicits - removable.random(random))
            }
            PoeCurrency.DIVINE -> {
                rarity(PoeRarity.MAGIC, PoeRarity.RARE)
                require(item.explicits.any { !protected(item, it) && variable(it) }) { "No rerollable explicit values" }
                item.copy(explicits = item.explicits.map { if (protected(item, it)) it else roll(it.id) })
            }
            PoeCurrency.BLESSED -> {
                require(item.implicits.any(::variable)) { "No rerollable implicit values" }
                item.copy(implicits = item.implicits.map { roll(it.id) })
            }
            PoeCurrency.FRACTURING -> {
                rarity(PoeRarity.RARE)
                require(item.explicits.size >= 4 && item.explicits.none { it.fractured }) { "Requires an unfractured rare with at least four modifiers" }
                val index = random.nextInt(item.explicits.size)
                item.copy(explicits = item.explicits.mapIndexed { i, r -> if (i == index) r.copy(fractured = true) else r })
            }
            PoeCurrency.MIRROR -> item.copy(mirrored = true) // service appends a new UUID and preserves the original
        }
        validate(result)
        return result
    }
    private fun variable(roll: PoeRoll) = catalog.mod(roll.id).objects("stats").any { it.int("min") != it.int("max") }
}
