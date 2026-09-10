package features.poe

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import features.data.equipment.equipment_data.*
import features.logic.modifiers.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

@Serializable
data class PoeRecord(val id: String, val data: JsonObject)

internal fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
internal fun JsonObject.int(key: String, default: Int = 0) = this[key]?.jsonPrimitive?.intOrNull ?: default
internal fun JsonObject.strings(key: String) = (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
internal fun JsonObject.objects(key: String) = (this[key] as? JsonArray)?.map { it.jsonObject }.orEmpty()

/** Open stat IDs and original records are retained, including unsupported combat effects.
 * No heuristic translation of a raw PoE stat into a combat formula is performed. */
class PoeCatalog(val bases: Map<String, JsonObject>, val mods: Map<String, JsonObject>,
    private val history: Map<Pair<String, Int>, JsonObject> = emptyMap(),
    private val heads: Map<String, Int> = emptyMap(),
    private val disabled: Set<String> = emptySet()) {
    companion object {
        val bundled: PoeCatalog by lazy {
            fun read(name: String): Map<String, JsonObject> {
                val stream = requireNotNull(PoeCatalog::class.java.getResourceAsStream("/poe/$name.json.gz")) {
                    "Missing PoE catalog; run preparePoeCatalog before packaging"
                }
                return GZIPInputStream(stream).bufferedReader().use { reader ->
                    Json.parseToJsonElement(reader.readText()).jsonObject.mapValues { it.value.jsonObject }
                }
            }
            PoeCatalog(read("base_items"), read("mods"))
        }
        val weaponClasses = setOf("One Hand Sword", "Thrusting One Hand Sword", "Two Hand Sword", "One Hand Axe", "Two Hand Axe", "One Hand Mace", "Two Hand Mace", "Sceptre", "Staff", "Warstaff", "Bow", "Wand", "Claw", "Dagger", "Rune Dagger")
        val slots = mapOf("Helmet" to EnumEquipmentType.HELMET, "Body Armour" to EnumEquipmentType.BODY,
            "Gloves" to EnumEquipmentType.GLOVES, "Boots" to EnumEquipmentType.BOOTS, "Shield" to EnumEquipmentType.SHIELD,
            "Ring" to EnumEquipmentType.RING, "Amulet" to EnumEquipmentType.AMULET,
            "Belt" to EnumEquipmentType.BELT, "Quiver" to EnumEquipmentType.QUIVER)
        fun stableId(key: String): String = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
    init {
        require(bases.isNotEmpty() && mods.isNotEmpty())
        bases.forEach { (id, base) ->
            require(base.strings("implicits").all { it in mods }) { "Missing implicit on $id" }
        }
        mods.forEach { (id, mod) ->
            require(mod.objects("stats").all { it.int("min") <= it.int("max") }) { "Invalid stat range: $id" }
        }
    }
    fun base(id: String) = requireNotNull(bases[id]) { "Unknown base: $id" }
    fun revision(id: String): Int = heads[id] ?: 1
    fun enabled(id: String) = id !in disabled
    fun mod(id: String, revision: Int = revision(id)): JsonObject =
        requireNotNull(history[id to revision] ?: mods[id]?.takeIf { revision == this.revision(id) }) { "Missing modifier revision: $id@$revision" }
    fun hasRevision(id: String, revision: Int) = history.containsKey(id to revision) || (id in mods && revision == this.revision(id))
    fun wearable(base: JsonObject) = base.string("item_class") in slots || base.string("item_class") in weaponClasses
    fun releasedWearables() = bases.filterValues { wearable(it) && it.string("release_state") == "released" }
    fun ordinaryDrop(base: JsonObject): Boolean = wearable(base) && base.string("release_state") == "released" &&
        base.strings("tags").none { it in setOf("not_for_sale", "atlas_base_type", "heist_base", "experimental_base", "talisman", "synthesised") } &&
        !base.string("name").contains("Talisman")

    fun legacyModifier(roll: PoeRoll, implicit: Boolean = false): Modifier {
        val definition = mod(roll.id, roll.revision)
        return Modifier(roll.id, roll.values.map { ModifierValue(it.toDouble()) }, 1,
            if (implicit) ModifierSource.BASE_ITEM else if (definition.string("generation_type") == "prefix") ModifierSource.PREFIX else ModifierSource.SUFFIX,
            definition.strings("implicit_tags").map(::ModifierTag).toSet(), definitionRevision = roll.revision)
    }
    fun definition(id: String): ModifierDefinition {
        val raw = mod(id)
        val generation = raw.string("generation_type")
        val source = when (generation) {
            "prefix" -> ModifierSource.PREFIX
            "suffix" -> ModifierSource.SUFFIX
            "corrupted" -> ModifierSource.CORRUPTION
            "unique" -> ModifierSource.UNIQUE
            "enchantment" -> ModifierSource.ENCHANTMENT
            else -> ModifierSource.BASE_ITEM
        }
        return ModifierDefinition(id = id, name = raw.string("text").ifBlank { raw.string("name").ifBlank { id } },
            source = source, affixType = when(generation) { "prefix" -> AffixType.PREFIX; "suffix" -> AffixType.SUFFIX; else -> null },
            tiers = listOf(ModifierTier(1, raw.int("required_level", 1).coerceAtLeast(1), 100,
                raw.objects("stats").map { ValueRange(it.int("min").toDouble(), it.int("max").toDouble()) })),
            tags = raw.strings("implicit_tags").map(::ModifierTag).toSet(),
            effects = PoeEffectRegistry().effects(raw),
            scope = ModifierScope.CHARACTER,
            // Unsupported effects remain explicit in the catalog capability report.
            runtimeSupported = PoeEffectRegistry().fullySupported(raw),
            unsupportedStats = PoeEffectRegistry().unsupported(raw),
            rollable = false, poe = raw, _id = stableId("modifier:$id"))
    }
    fun equipment(id: String): Equipment {
        val b = base(id)
        require(wearable(b))
        val klass = b.string("item_class")
        val p = b["properties"]!!.jsonObject
        val equipment = when {
            klass in weaponClasses -> Weapon(
                slot = if (klass.startsWith("Two Hand") || klass in setOf("Staff", "Warstaff", "Bow")) EnumEquipmentType.WEAPON_2H else EnumEquipmentType.WEAPON_1H,
                weaponType = when (klass) {
                    "Bow" -> EnumEquipmentWeapon.BOW
                    "Wand" -> EnumEquipmentWeapon.WAND
                    "One Hand Axe" -> EnumEquipmentWeapon.AXE
                    "Two Hand Axe" -> EnumEquipmentWeapon.DOUBLEAXE
                    "Two Hand Sword" -> EnumEquipmentWeapon.DOUBLESWORD
                    "One Hand Mace" -> EnumEquipmentWeapon.MACE
                    "Two Hand Mace" -> EnumEquipmentWeapon.TWO_HAND_MACE
                    "Sceptre" -> EnumEquipmentWeapon.SCEPTRE
                    "Staff" -> EnumEquipmentWeapon.STAFF
                    "Warstaff" -> EnumEquipmentWeapon.WARSTAFF
                    "Claw" -> EnumEquipmentWeapon.CLAW
                    "Dagger" -> EnumEquipmentWeapon.DAGGER
                    "Rune Dagger" -> EnumEquipmentWeapon.RUNE_DAGGER
                    "Thrusting One Hand Sword" -> EnumEquipmentWeapon.THRUSTING_SWORD
                    else -> EnumEquipmentWeapon.SWORD
                }, damage_min = p.int("physical_damage_min").toDouble(), damage_max = p.int("physical_damage_max").toDouble(),
                attackSpeed = 1000.0 / p.int("attack_time", 1000).coerceAtLeast(1), durability = 100)
            klass in setOf("Helmet", "Body Armour", "Gloves", "Boots", "Shield") -> Armor(slots.getValue(klass),
                (p["armour"] as? JsonObject)?.int("min") ?: 0)
            else -> Accessory(slots.getValue(klass))
        }
        equipment._id = stableId("base:$id")
        equipment.poeBaseId = id
        equipment.name = b.string("name")
        equipment.itemLevel = b.int("drop_level", 1).coerceAtLeast(1)
        equipment.description = "PoE ${b.string("item_class")}; base properties and modifiers: /api/v1/poe/catalog"
        equipment.stockModifierDefinitionRefs = b.strings("implicits").map { ModifierRef(it, revision(it)) }
        equipment.price = equipment.calculatePrice()
        return equipment
    }
}
