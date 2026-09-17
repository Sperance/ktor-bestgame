package features.character

import kotlin.reflect.jvm.javaField
import kotlin.test.*
import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.EquipmentSlot
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.domain.PoeCrafting
import ru.descend.features.poe.domain.PoeInventory
import ru.descend.features.poe.domain.PoeRarity
import ru.descend.infrastructure.http.AppJson

/**
 * Документ персонажа не должен расти вместе с инвентарём: экипировка хранится отдельной
 * коллекцией, а [Character.equipments] — это рабочий набор в памяти.
 */
class CharacterStorageTest {
    private val catalog = PoeCatalog.bundled
    private fun item(): CharacterEquipments {
        val engine = PoeCrafting(catalog)
        val baseId = catalog.bases.entries.first { it.value.string("name") == "Iron Ring" }.key
        return PoeInventory(catalog, engine).fromState(engine.generate(baseId, 85, PoeRarity.NORMAL))
    }

    @Test fun serializedCharacterCarriesSlotsButNotTheInventory() {
        val ring = item()
        val character = Character("owner", "hero", equipments = mutableListOf(ring),
            equipped = mapOf(EquipmentSlot.RING_LEFT to ring.uuid))
        val json = AppJson.encodeToString(Character.serializer(), character)
        assertFalse(json.contains("\"equipments\""), "Инвентарь не должен попадать в документ персонажа")
        assertFalse(json.contains("\"items\""), "Стаков предметов в документе персонажа больше нет")
        assertTrue(json.contains("\"equipped\""))
        assertFalse(json.contains("baseSnapshot"), "Снимок базы предмета остаётся в его собственном документе")
        val restored = AppJson.decodeFromString(Character.serializer(), json)
        assertTrue(restored.equipments.isEmpty(), "Рабочий набор заполняется чтением из коллекции предметов")
        assertEquals(character.equipped, restored.equipped)
    }

    /** Точечный update в BaseRepository пропускает @Transient по этому же признаку. */
    @Test fun inventoryFieldIsMarkedTransient() {
        val property = Character::equipments
        assertTrue(property.annotations.any { it is kotlinx.serialization.Transient } ||
            property.javaField?.annotations?.any { it is kotlinx.serialization.Transient } == true)
    }

    @Test fun decodingIgnoresTheLegacyEmbeddedArrays() {
        val ring = item()
        val legacy = AppJson.encodeToString(Character.serializer(), Character("owner", "hero"))
            .replaceFirst("{", "{\"equipments\":[" + AppJson.encodeToString(CharacterEquipments.serializer(), ring) + "]," +
                "\"items\":[{\"itemId\":\"deadbeefdeadbeefdeadbeef\",\"amount\":7}],")
        val restored = AppJson.decodeFromString(Character.serializer(), legacy)
        assertTrue(restored.equipments.isEmpty(), "Старый массив читается, но больше не заполняет рабочий набор")
        assertEquals("hero", restored.name)
    }

    /** Поля количества в модели принадлежащих предметов нет — это и означает отказ от стаков. */
    @Test fun ownedItemCarriesNoAmount() {
        val fields = ru.descend.features.character.model.CharacterInventoryItem.serializer().descriptor
        val names = (0 until fields.elementsCount).map { fields.getElementName(it) }
        assertTrue("itemId" in names && "characterId" in names)
        assertFalse("amount" in names, "Единица предмета не должна нести количество")
    }
}
