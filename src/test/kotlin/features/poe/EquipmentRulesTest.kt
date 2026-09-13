package features.poe

import kotlin.test.*
import ru.descend.features.character.model.*
import ru.descend.features.character.domain.*
import ru.descend.features.poe.catalog.*
import ru.descend.features.poe.domain.*
import ru.descend.shared.http.ApiFailure
import kotlinx.serialization.json.*

class EquipmentRulesTest {
    private val catalog = PoeCatalog.bundled
    private fun item(name: String, id: String) = PoeInventory(catalog, PoeCrafting(catalog)).fromState(PoeCrafting(catalog).generate(catalog.bases.entries.first { it.value.string("name") == name }.key, 85, PoeRarity.NORMAL), id)
    private fun stats(c: Character, cat: PoeCatalog = catalog) = CharacterStatsCalculator().calculate(c, emptyMap(), cat)
    private fun validate(c: Character) = EquipmentRules.validate(c, emptyMap(), catalog) { stats(it) }
    @Test fun inventoryDoesNotGrantStatsUntilEquippedAndRequirementsExcludeSelf() {
        val ring = item("Coral Ring", "ring")
        val c = Character("owner", "test", equipments = mutableListOf(ring))
        val bare = stats(c).values.getValue("maximum_life")
        val equipped = c.copy(equipped = mapOf(EquipmentSlot.RING_LEFT to ring.uuid))
        assertTrue(stats(equipped).values.getValue("maximum_life") > bare)
        validate(equipped)
        assertEquals(bare, stats(equipped.copy(equipped = emptyMap())).values.getValue("maximum_life"))
    }
    @Test fun slotsAndHandCombinationsAreValidated() {
        val bow = item("Crude Bow", "bow"); val shield = item("Splintered Tower Shield", "shield"); val ring = item("Iron Ring", "ring")
        val c = Character("owner", "test", level = 85, equipments = mutableListOf(bow, shield, ring))
        assertFailsWith<ApiFailure> { validate(c.copy(equipped = mapOf(EquipmentSlot.HELMET to ring.uuid))) }
        assertFailsWith<ApiFailure> { validate(c.copy(equipped = mapOf(EquipmentSlot.RING_LEFT to ring.uuid, EquipmentSlot.RING_RIGHT to ring.uuid))) }
        assertFailsWith<ApiFailure> { validate(c.copy(equipped = mapOf(EquipmentSlot.MAIN_HAND to bow.uuid, EquipmentSlot.OFF_HAND to shield.uuid))) }
    }
    @Test fun localDamageDoesNotImproveTheOtherWeapon() {
        val sword = item("Rusted Sword", "sword")
        val boosted = sword.copy(uuid = "boosted", poe = sword.poe!!.copy(rarity = PoeRarity.MAGIC, explicits = listOf(PoeRoll("LocalIncreasedPhysicalDamagePercent1", listOf(40)))))
        val c = Character("owner", "test", equipments = mutableListOf(sword, boosted), equipped = mapOf(EquipmentSlot.MAIN_HAND to sword.uuid, EquipmentSlot.OFF_HAND to boosted.uuid))
        val calculated = stats(c)
        assertEquals(4.0, calculated.weapons.getValue(EquipmentSlot.MAIN_HAND).minimumPhysical)
        assertEquals(5.6, calculated.weapons.getValue(EquipmentSlot.OFF_HAND).minimumPhysical, 0.00001)
    }
    @Test fun selfGrantedAttributesCannotBypassRequirements() {
        val sword = item("Eternal Sword", "sword")
        val raw = JsonObject(catalog.mod("Strength1") + ("stats" to buildJsonArray { add(buildJsonObject { put("id", "additional_strength"); put("min", 1000); put("max", 1000) }) }))
        val modified = PoeCatalog(catalog.bases, catalog.mods + ("self_strength" to raw))
        sword.poe = sword.poe!!.copy(explicits = listOf(PoeRoll("self_strength", listOf(1000))))
        val c = Character("owner", "test", level = 85, equipments = mutableListOf(sword), equipped = mapOf(EquipmentSlot.MAIN_HAND to sword.uuid))
        assertFailsWith<ApiFailure> { EquipmentRules.validate(c, emptyMap(), modified) { stats(it, modified) } }
    }
}
