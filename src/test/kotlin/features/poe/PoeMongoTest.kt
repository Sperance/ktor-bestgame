package features.poe

import application.koin.repositoryModule
import application.koin.cacheModule
import config.MongoFactory
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.equipment.EquipmentRepository
import features.logic.modifiers.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.Koin
import org.bson.types.ObjectId
import kotlin.test.*

class PoeMongoTest {
    private lateinit var koin: Koin
    @Before fun setup() {
        require(System.getenv("MONGO_DB") == "poe_integration_test") { "Integration tests require dedicated MONGO_DB=poe_integration_test" }
        koin = startKoin { modules(repositoryModule, cacheModule) }.koin
        runBlocking {
            if (koin.get<ModifierDefinitionRepository>().count() == 0L) PoeSeeder.seed()
        }
    }
    @After fun tearDown() { stopKoin() }
    private suspend fun fixture(): Pair<Character, CraftRequest> {
        val catalog = PoeCatalog.bundled
        val engine = PoeCrafting(catalog)
        val inventory = PoeInventory(catalog, engine)
        val baseId = catalog.bases.entries.first { it.value.string("name") == "Iron Ring" && it.value.string("release_state") == "released" }.key
        val item = inventory.fromState(engine.generate(baseId, 85, PoeRarity.NORMAL))
        val character = Character(ObjectId().toHexString(), "test_${ObjectId().toHexString()}", equipments = mutableListOf(item),
            items = mutableListOf(CharacterItems(inventory.currencyId(PoeCurrency.ALCHEMY), 3)))
        koin.get<CharacterRepository>().collection.insertOne(character)
        return character to CraftRequest("request_${ObjectId().toHexString()}", item.uuid, PoeCurrency.ALCHEMY, 0)
    }
    @Test fun commitAndReplayDoNotDoubleSpend(): Unit = runBlocking {
        val (character, request) = fixture()
        val repo = koin.get<CharacterRepository>()
        val service = PoeService(repo, koin.get(), koin.get())
        val first = service.craft(character._id, character.userId, request)
        val replay = service.craft(character._id, character.userId, request)
        assertEquals(first, replay)
        val stored = repo.findById(character._id)!!
        assertEquals(1L, stored.version)
        assertEquals(2L, stored.items.single().amount)
        assertEquals(first.equipment, stored.equipments.single())
        assertFailsWith<IllegalArgumentException> { service.craft(character._id, character.userId, request.copy(currency = PoeCurrency.CHAOS)) }
        assertFailsWith<IllegalArgumentException> { service.craft(character._id, "intruder", request) }
    }
    @Test fun competingRequestsOnlyCommitOneDebit() = runBlocking {
        val (character, request) = fixture()
        val repo = koin.get<CharacterRepository>()
        val service = PoeService(repo, koin.get(), koin.get())
        val results = coroutineScope {
            (1..2).map { n -> async(Dispatchers.Default) {
                runCatching { service.craft(character._id, character.userId, request.copy(requestId = request.requestId + n)) }
            } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        val stored = repo.findById(character._id)!!
        assertEquals(2L, stored.items.single().amount)
        assertEquals(1L, stored.version)
    }
    @Test fun invalidOperationLeavesDatabaseUnchanged() = runBlocking {
        val (character, request) = fixture()
        val repo = koin.get<CharacterRepository>()
        val service = PoeService(repo, koin.get(), koin.get())
        assertFailsWith<IllegalArgumentException> { service.craft(character._id, character.userId, request.copy(expectedVersion = 22)) }
        val stored = repo.findById(character._id)!!
        assertEquals(0L, stored.version)
        assertEquals(character.items, stored.items)
        assertEquals(character.equipments, stored.equipments)
    }
    @Test fun seederIsAdditiveAndDoesNotGrantItemsAgain() = runBlocking {
        val (character, _) = fixture()
        PoeSeeder.seed()
        val repo = koin.get<EquipmentRepository>()
        val count = repo.count()
        val first = repo.findAll().first()
        first.name = "ADMIN CUSTOM NAME"
        MongoFactory.transactionExecute { repo.update(first, it) }
        PoeSeeder.seed()
        assertEquals(count, repo.count())
        assertEquals("ADMIN CUSTOM NAME", repo.findById(first._id)!!.name)
        assertEquals(character.equipments, koin.get<CharacterRepository>().findById(character._id)!!.equipments)
        assertTrue(koin.get<ModifierDefinitionRepository>().count() >= 40355L)
    }
    @Test fun mongoRevisionChangesNewRollsButNotExistingItems(): Unit = runBlocking {
        val definitions = koin.get<ModifierDefinitionRepository>()
        val catalogs = koin.get<MongoModifierCatalog>()
        val id = "test_mod_${ObjectId().toHexString()}"
        fun raw(min: Int, max: Int) = JsonObject(PoeCatalog.bundled.mod("Strength1") + ("stats" to buildJsonArray {
            add(buildJsonObject { put("id", "additional_strength"); put("min", min); put("max", max) })
        }))
        val first = definitions.publish(ModifierDefinition(id, "Test strength", ModifierSource.PREFIX, poe = raw(8, 12)), 0)
        assertEquals(ModifierSource.SUFFIX, first.source)
        assertEquals(8.0, first.tiers.single().values.single().min)
        assertFailsWith<IllegalArgumentException> { definitions.publish(first.copy(poe = null), 1) }
        catalogs.invalidate()
        val oldCatalog = catalogs.snapshot().catalog
        val baseId = oldCatalog.bases.entries.first { it.value.string("name") == "Iron Ring" && it.value.string("release_state") == "released" }.key
        val oldState = PoeCrafting(oldCatalog).generate(baseId, 85, PoeRarity.NORMAL)
            .copy(rarity = PoeRarity.MAGIC, explicits = listOf(PoeRoll(id, listOf(10), revision = first.revision)))
        definitions.publish(first.copy(poe = raw(100, 200)), 1)
        catalogs.invalidate()
        val current = catalogs.snapshot().catalog
        val engine = PoeCrafting(current)
        assertEquals(2, engine.roll(id).revision)
        assertTrue(engine.roll(id).values.single() in 100..200)
        val changed = engine.apply(oldState, PoeCurrency.DIVINE)
        assertEquals(1, changed.explicits.single().revision)
        assertTrue(changed.explicits.single().values.single() in 8..12)
        assertEquals(raw(8, 12).canonicalDefinitionJson(), definitions.resolve(ModifierRef(id, 1))!!.poe!!.canonicalDefinitionJson())
        definitions.publish(first.copy(enabled = false, poe = raw(100, 200)), 2)
        catalogs.invalidate()
        val disabled = PoeCrafting(catalogs.snapshot().catalog)
        assertFalse(id in disabled.eligible(oldState.copy(explicits = emptyList())))
        assertEquals(1, disabled.apply(oldState, PoeCurrency.DIVINE).explicits.single().revision)
    }

    @Test fun publicationRejectsStaleRevisionAndConcurrentDuplicate(): Unit = runBlocking {
        val definitions = koin.get<ModifierDefinitionRepository>()
        val id = "concurrent_${ObjectId().toHexString()}"
        val definition = ModifierDefinition(id, "Concurrent", ModifierSource.PREFIX)
        val results = coroutineScope { (1..2).map { async(Dispatchers.Default) {
            runCatching { definitions.publish(definition, 0) }
        } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, definitions.latest(id)!!.revision)
        assertFailsWith<IllegalArgumentException> { definitions.publish(definition, 0) }
    }

    @Test fun embeddedMigrationPreservesConflictingLocalDefinitionAndRolls(): Unit = runBlocking {
        val definitions = koin.get<ModifierDefinitionRepository>()
        val equipment = koin.get<EquipmentRepository>()
        val characters = koin.get<CharacterRepository>()
        val id = "legacy_${ObjectId().toHexString()}"
        val canonical = ModifierDefinition(id, "Canonical", ModifierSource.PREFIX, tiers = listOf(ModifierTier(1, values = listOf(ValueRange(1.0, 2.0)))))
        definitions.publish(canonical, 0)
        val embedded = canonical.copy(name = "Item-local", tiers = listOf(ModifierTier(1, values = listOf(ValueRange(7.0, 9.0)))))
        val template = features.data.equipment.equipment_data.Armor(application.enums.EnumEquipmentType.HELMET, 1,
            modifierDefinitions = listOf(embedded))
        equipment.collection.insertOne(template) // simulate data written by the old schema
        val instance = features.data.character.character_data.CharacterEquipments(template._id,
            params = mutableListOf(Modifier(id, listOf(ModifierValue(8.0)), 1, ModifierSource.PREFIX)))
        val owner = Character(ObjectId().toHexString(), "migration_${ObjectId().toHexString()}", equipments = mutableListOf(instance))
        characters.collection.insertOne(owner)
        val migration = ModifierReferenceMigration(equipment, characters, definitions)
        migration.migrate()
        val migrated = equipment.findById(template._id)!!
        assertNull(migrated.modifierDefinitions)
        val ref = migrated.modifierDefinitionRefs.single()
        assertNotEquals(id, ref.definitionId)
        assertEquals("Item-local", definitions.resolve(ref)!!.name)
        val stored = characters.findById(owner._id)!!
        assertEquals(instance.uuid, stored.equipments.single().uuid)
        assertEquals(8.0, stored.equipments.single().params.single().value)
        assertEquals(ref.definitionId, stored.equipments.single().params.single().definitionId)
        migration.migrate()
        assertEquals(stored.version, characters.findById(owner._id)!!.version)
        assertEquals("Canonical", definitions.resolve(ModifierRef(id, 1))!!.name)
    }

    @Test fun newEquipmentRejectsEmbeddedDefinitionsAndDanglingReferences(): Unit = runBlocking {
        val equipment = koin.get<EquipmentRepository>()
        val template = features.data.equipment.equipment_data.Armor(application.enums.EnumEquipmentType.HELMET, 1,
            modifierDefinitions = listOf(ModifierDefinition("inline", "Inline", ModifierSource.PREFIX)))
        assertFailsWith<IllegalArgumentException> { MongoFactory.transactionExecute { equipment.insert(template, it) } }
        template.modifierDefinitions = null
        template.modifierDefinitionRefs = listOf(ModifierRef("missing_${ObjectId().toHexString()}"))
        assertFailsWith<IllegalArgumentException> { MongoFactory.transactionExecute { equipment.insert(template, it) } }
        template.modifierDefinitionRefs = emptyList()
        MongoFactory.transactionExecute { equipment.insert(template, it) }
        assertFailsWith<IllegalArgumentException> {
            MongoFactory.transactionExecute { equipment.updateFields(template._id, mapOf("stockModifierDefinitionRefs" to null), it) }
        }
        assertTrue(equipment.findById(template._id)!!.stockModifierDefinitionRefs.isEmpty())
    }

    @Test fun malformedPoeRangesCannotBePublished(): Unit = runBlocking {
        val definitions = koin.get<ModifierDefinitionRepository>()
        val id = "invalid_${ObjectId().toHexString()}"
        val raw = JsonObject(PoeCatalog.bundled.mod("Strength1") + ("stats" to buildJsonArray {
            add(buildJsonObject { put("id", "additional_strength"); put("min", "not_a_number"); put("max", 100) })
        }))
        assertFailsWith<IllegalArgumentException> { definitions.publish(ModifierDefinition(id, "Invalid", ModifierSource.SUFFIX, poe = raw), 0) }
        assertNull(definitions.latest(id))
    }

    @Test fun catalogReloadsOnlyAfterCommittedPublication(): Unit = runBlocking {
        val definitions = koin.get<ModifierDefinitionRepository>()
        val reader = MongoModifierCatalog(definitions, checkIntervalNanos = 0)
        val first = reader.snapshot()
        assertSame(first, reader.snapshot())
        val id = "clock_${ObjectId().toHexString()}"
        definitions.publish(ModifierDefinition(id, "Clock", ModifierSource.PREFIX), 0)
        val changed = reader.snapshot() // another process need not call invalidate()
        assertNotSame(first, changed)
        assertEquals(id, changed.resolve(ModifierRef(id, 1)).id)
        assertSame(changed, reader.snapshot())
    }

}
