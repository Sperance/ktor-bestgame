package features.poe

import features.data.character.Character
import features.data.character.character_data.CharacterItems
import kotlin.random.Random
import kotlin.test.*

class PoeCraftingTest {
    private val catalog = PoeCatalog.bundled
    private val engine = PoeCrafting(catalog, Random(1234))
    private val baseId = catalog.bases.entries.first { it.value.string("name") == "Iron Ring" && it.value.string("release_state") == "released" }.key
    private fun normal() = engine.generate(baseId, 85, PoeRarity.NORMAL)
    private fun rare() = engine.apply(normal(), PoeCurrency.ALCHEMY)

    @Test fun catalogIncludesCompletePinnedExport() {
        assertEquals(5461, catalog.bases.size)
        assertEquals(40355, catalog.mods.size)
        assertTrue(catalog.releasedWearables().size > 700)
        assertEquals(catalog.equipment(baseId)._id, catalog.equipment(baseId)._id)
        assertEquals(catalog.base(baseId).strings("implicits"), normal().implicits.map { it.id })
    }
    @Test fun magicAndRareLimitsAcrossManyRolls() {
        repeat(200) {
            val magic = engine.apply(normal(), PoeCurrency.TRANSMUTATION)
            assertTrue(magic.explicits.size in 1..2)
            val rare = rare()
            assertTrue(rare.explicits.size in 4..6)
            engine.validate(magic); engine.validate(rare)
            assertEquals(rare.implicits, engine.apply(rare, PoeCurrency.CHAOS).implicits)
        }
    }
    @Test fun regalPreservesExistingModifiers() {
        val magic = engine.apply(normal(), PoeCurrency.TRANSMUTATION)
        val rare = engine.apply(magic, PoeCurrency.REGAL)
        assertEquals(PoeRarity.RARE, rare.rarity)
        assertEquals(magic.explicits.size + 1, rare.explicits.size)
        assertTrue(rare.explicits.containsAll(magic.explicits))
    }
    @Test fun exaltAddsOneAndRejectsFullItem() {
        var item = rare()
        while(item.explicits.size < 6) {
            val next = engine.apply(item, PoeCurrency.EXALTED)
            assertEquals(item.explicits.size + 1, next.explicits.size)
            item = next
        }
        assertFailsWith<IllegalArgumentException> { engine.apply(item, PoeCurrency.EXALTED) }
    }
    @Test fun scouringKeepsImplicitAndFracture() {
        val fractured = engine.apply(rare(), PoeCurrency.FRACTURING)
        val fixed = fractured.explicits.single { it.fractured }
        val clean = engine.apply(fractured, PoeCurrency.SCOURING)
        assertEquals(listOf(fixed), clean.explicits)
        assertEquals(PoeRarity.MAGIC, clean.rarity)
        assertEquals(fractured.implicits, clean.implicits)
        repeat(30) { assertTrue(engine.apply(fractured, PoeCurrency.CHAOS).explicits.contains(fixed)) }
    }
    @Test fun divineDoesNotChangeTierOrFracture() {
        val original = engine.apply(rare(), PoeCurrency.FRACTURING)
        val result = engine.apply(original, PoeCurrency.DIVINE)
        assertEquals(original.explicits.map { it.id }, result.explicits.map { it.id })
        assertEquals(original.explicits.single { it.fractured }, result.explicits.single { it.fractured })
        assertEquals(original.implicits, result.implicits)
    }
    @Test fun annulRemovesOneAndKeepsRarity() {
        val item = rare()
        val result = engine.apply(item, PoeCurrency.ANNULMENT)
        assertEquals(item.explicits.size - 1, result.explicits.size)
        assertEquals(item.rarity, result.rarity)
        assertTrue(item.explicits.containsAll(result.explicits))
    }
    @Test fun immutableGuardsAndRarityChecks() {
        assertFailsWith<IllegalArgumentException> { engine.apply(normal(), PoeCurrency.CHAOS) }
        assertFailsWith<IllegalArgumentException> { engine.apply(rare().copy(corrupted = true), PoeCurrency.DIVINE) }
        assertFailsWith<IllegalArgumentException> { engine.apply(rare().copy(mirrored = true), PoeCurrency.SCOURING) }
        assertFailsWith<IllegalArgumentException> { engine.apply(rare().copy(influences = setOf("shaper")), PoeCurrency.FRACTURING) }
    }
    @Test fun currencyDebitAndItemReplacementAreOnePureTransition() {
        val inventory = PoeInventory(catalog, engine)
        val item = inventory.fromState(normal())
        val currencyId = inventory.currencyId(PoeCurrency.ALCHEMY)
        val character = Character("owner", "test", equipments = mutableListOf(item), items = mutableListOf(CharacterItems(currencyId, 2)))
        val request = CraftRequest("request_123", item.uuid, PoeCurrency.ALCHEMY, 0)
        val (next, result) = inventory.craft(character, "owner", request)
        assertEquals(1L, next.items.single().amount)
        assertEquals(2L, character.items.single().amount)
        assertEquals(PoeRarity.NORMAL, character.equipments.single().poe!!.rarity)
        assertEquals(PoeRarity.RARE, result.equipment.poe!!.rarity)
        assertEquals(item.uuid, result.equipment.uuid)
        assertEquals(1L, result.characterVersion)
        assertFailsWith<IllegalArgumentException> { inventory.craft(character, "intruder", request) }
        assertFailsWith<IllegalArgumentException> { inventory.craft(character, "owner", request.copy(expectedVersion = 1)) }
        assertFailsWith<IllegalArgumentException> { inventory.craft(character, "owner", request.copy(equipmentUuid = "foreign")) }
    }
    @Test fun invalidCraftDoesNotDebitOrMutate() {
        val inventory = PoeInventory(catalog, engine)
        val item = inventory.fromState(normal())
        val character = Character("owner", "test", equipments = mutableListOf(item), items = mutableListOf(CharacterItems(inventory.currencyId(PoeCurrency.CHAOS), 2)))
        assertFailsWith<IllegalArgumentException> { inventory.craft(character, "owner", CraftRequest("request_123", item.uuid, PoeCurrency.CHAOS, 0)) }
        assertEquals(2L, character.items.single().amount)
        assertEquals(PoeRarity.NORMAL, item.poe!!.rarity)
    }
    @Test fun mirrorCreatesNewUuidAndPreservesOriginal() {
        val inventory = PoeInventory(catalog, engine)
        val item = inventory.fromState(rare())
        val character = Character("owner", "test", equipments = mutableListOf(item), items = mutableListOf(CharacterItems(inventory.currencyId(PoeCurrency.MIRROR), 1)))
        val (next, result) = inventory.craft(character, "owner", CraftRequest("request_123", item.uuid, PoeCurrency.MIRROR, 0))
        assertEquals(2, next.equipments.size)
        assertEquals(item, next.equipments.first())
        assertNotEquals(item.uuid, result.equipment.uuid)
        assertTrue(result.equipment.poe!!.mirrored)
        assertTrue(next.items.isEmpty())
    }
    @Test fun serializationRoundTripPreservesIndividualState() {
        val inventory = PoeInventory(catalog, engine)
        val item = inventory.fromState(engine.apply(rare(), PoeCurrency.FRACTURING))
        val json = server.addons.AppJson
        val serializer = features.data.character.character_data.CharacterEquipments.serializer()
        assertEquals(item, json.decodeFromString(serializer, json.encodeToString(serializer, item)))
    }
}
