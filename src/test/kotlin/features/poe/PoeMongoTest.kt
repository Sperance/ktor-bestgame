package features.poe

import application.koin.repositoryModule
import application.koin.cacheModule
import config.MongoFactory
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.equipment.EquipmentRepository
import features.logic.modifiers.ModifierDefinitionRepository
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
    @Test fun commitAndReplayDoNotDoubleSpend() = runBlocking {
        val (character, request) = fixture()
        val repo = koin.get<CharacterRepository>()
        val service = PoeService(repo, koin.get())
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
        val service = PoeService(repo, koin.get())
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
        val service = PoeService(repo, koin.get())
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
        assertEquals(40355L, koin.get<ModifierDefinitionRepository>().count())
    }
}
