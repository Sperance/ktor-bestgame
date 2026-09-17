package ru.descend.features.character.application

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import ru.descend.domain.modifiers.ModifierRef
import ru.descend.features.character.model.*
import ru.descend.features.character.domain.*
import ru.descend.features.character.persistence.CharacterEquipmentRepository
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.poe.persistence.MongoModifierCatalog
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.Actor
import ru.descend.shared.http.*

/**
 * Страница инвентаря. Пагинация курсорная: [next] — uuid для параметра `after` следующего запроса,
 * `null` — предметов больше нет. Такой обход не деградирует и на миллионе предметов.
 */
@Serializable data class InventoryPage(val items: List<CharacterEquipments>, val size: Int, val total: Long, val next: String? = null)

@Serializable data class EquipmentView(val characterVersion: Long, val equipped: Map<EquipmentSlot, String>,
    val equippedItems: List<CharacterEquipments>, val inventory: InventoryPage,
    val items: List<CharacterItems>, val stats: CharacterStats)

/**
 * Экипировка живёт в собственной коллекции, поэтому сервис никогда не поднимает инвентарь целиком:
 * расчёту нужны только надетые предметы, команде — ещё и адресуемый ею uuid, а списку — одна страница.
 * Логика модификаторов при этом не изменилась: она по-прежнему читает [Character.equipments].
 */
class EquipmentService(private val characters: CharacterRepository, private val equipment: EquipmentRepository,
    private val catalogs: MongoModifierCatalog, private val inventory: CharacterEquipmentRepository,
    private val passiveTrees: ru.descend.features.passives.persistence.PassiveTreeRepository = ru.descend.features.passives.persistence.PassiveTreeRepository()) {

    /**
     * Догружает в рабочий набор надетые предметы и [extra] — uuid, которых требует команда.
     * Идемпотентна: уже подгруженное повторно не читается.
     */
    suspend fun hydrate(character: Character, extra: Collection<String> = emptyList(), session: ClientSession? = null): Character {
        val needed = (character.equipped.values + extra).toSet()
        val missing = needed - character.equipments.map { it.uuid }.toSet()
        if (missing.isEmpty()) return character
        val loaded = inventory.byUuids(character._id, missing, session)
        return character.copy(equipments = (character.equipments + loaded).toMutableList())
    }

    suspend fun context(character: Character): Pair<Map<String, Equipment>, MongoModifierCatalog.Snapshot> {
        val refs = character.params.map { ModifierRef(it.definitionId, it.definitionRevision) } + character.equipments.flatMap { item ->
            item.poe?.let { p -> (p.implicits + p.explicits).map { ModifierRef(it.id, it.revision) } } ?: item.params.map { ModifierRef(it.definitionId, it.definitionRevision) }
        }
        val templates = if (character.equipments.isEmpty()) emptyMap() else equipment.collection.find(Filters.`in`("_id", character.equipments.map { it.equipmentId })).toList().associateBy { it._id }
        return templates to catalogs.snapshot(refs)
    }
    suspend fun validate(character: Character, session: ClientSession? = null) {
        val hydrated = hydrate(character, session = session)
        val (templates, snapshot) = context(hydrated)
        val passiveTree = passiveTrees.definition(hydrated.passiveTreeRevision)
        EquipmentRules.validate(hydrated, templates, snapshot.catalog) { c -> CharacterStatsCalculator().calculate(c, templates, snapshot.catalog, snapshot.definitions, passiveTree) }
    }
    /** Характеристики персонажа: читает только надетые предметы, инвентарь не трогает. */
    suspend fun stats(character: Character, session: ClientSession? = null): CharacterStats {
        val hydrated = hydrate(character, session = session)
        val (templates, snapshot) = context(hydrated)
        return CharacterStatsCalculator().calculate(hydrated, templates, snapshot.catalog, snapshot.definitions, passiveTrees.definition(hydrated.passiveTreeRevision))
    }
    suspend fun character(characterId: String, actor: Actor): Character {
        val character = characters.findById(checkedId(characterId)) ?: missing(); actor.own(character); return character
    }
    /** Надетое читается по uuid из [Character.equipped] — инвентарь при этом не перебирается. */
    suspend fun equipped(characterId: String, actor: Actor): List<CharacterEquipments> =
        hydrate(character(characterId, actor)).equipments
    suspend fun view(characterId: String, actor: Actor, size: Int = CharacterEquipmentRepository.DEFAULT_PAGE_SIZE, after: String? = null): EquipmentView =
        view(character(characterId, actor), size, after)
    suspend fun view(character: Character, size: Int = CharacterEquipmentRepository.DEFAULT_PAGE_SIZE, after: String? = null): EquipmentView {
        if (size !in 1..CharacterEquipmentRepository.MAX_PAGE_SIZE) invalid("Invalid inventory page size")
        val hydrated = hydrate(character)
        val page = inventory.page(hydrated._id, size, after)
        return EquipmentView(hydrated.version, hydrated.equipped,
            hydrated.equipments.filter { it.uuid in hydrated.equipped.values },
            InventoryPage(page, size, inventory.count(hydrated._id), if (page.size < size) null else page.last().uuid),
            hydrated.items, stats(hydrated))
    }
    suspend fun compare(id: String, actor: Actor, command: EquipCommand): EquipmentComparison {
        val loaded = characters.findById(checkedId(id)) ?: missing(); actor.own(loaded)
        checkVersion(loaded.version, command.expectedVersion)
        val old = hydrate(loaded, listOf(command.equipmentUuid))
        if(old.equipments.none { it.uuid == command.equipmentUuid }) missing()
        val next = old.copy(equipped = old.equipped.filterValues { it != command.equipmentUuid } + (command.slot to command.equipmentUuid))
        val (templates, snapshot) = context(old)
        val passiveTree = passiveTrees.definition(old.passiveTreeRevision)
        fun calculate(c: Character) = CharacterStatsCalculator().calculate(c, templates, snapshot.catalog, snapshot.definitions, passiveTree)
        val before = calculate(old)
        return try {
            EquipmentRules.validate(next, templates, snapshot.catalog, ::calculate)
            EquipmentComparison(old.version, true, before = before, after = calculate(next))
        } catch(e: ApiFailure) {
            if(e.status != io.ktor.http.HttpStatusCode.BadRequest) throw e
            EquipmentComparison(old.version, false, e.message, before)
        }
    }
    suspend fun craftOptions(id: String, actor: Actor, uuid: String): CraftOptions {
        val loaded = characters.findById(checkedId(id)) ?: missing(); actor.own(loaded)
        val character = hydrate(loaded, listOf(uuid))
        val item = character.equipments.singleOrNull { it.uuid == uuid } ?: missing()
        val (_, snapshot) = context(character)
        val engine = ru.descend.features.poe.domain.PoeCrafting(snapshot.catalog, kotlin.random.Random(0))
        val poeInventory = ru.descend.features.poe.domain.PoeInventory(snapshot.catalog, engine)
        val options = ru.descend.features.poe.domain.PoeCurrency.entries.map { currency ->
            val currencyId = poeInventory.currencyId(currency)
            val amount = character.items.filter { it.itemId == currencyId }.sumOf { it.amount }
            val reason = when {
                character.userId != actor.id -> "Only the owner can craft this item"
                amount <= 0 -> "Not enough currency"
                item.poe == null -> "Legacy equipment requires migration"
                else -> try { engine.apply(item.poe!!, currency); null } catch(e: IllegalArgumentException) { e.message ?: "Currency is unavailable" }
            }
            CraftOption(currency.name, currency.displayName, currencyId, amount, reason == null, reason)
        }
        return CraftOptions(character.version, options)
    }
    suspend fun equip(id: String, actor: Actor, command: EquipCommand): EquipmentView = change(id, actor, command.expectedVersion, listOf(command.equipmentUuid)) { character, _ ->
        if (character.equipments.none { it.uuid == command.equipmentUuid }) missing()
        character.copy(equipped = character.equipped.filterValues { it != command.equipmentUuid } + (command.slot to command.equipmentUuid))
    }
    suspend fun unequip(id: String, actor: Actor, command: UnequipCommand): EquipmentView = change(id, actor, command.expectedVersion) { character, _ ->
        character.copy(equipped = character.equipped - command.slot)
    }
    suspend fun grant(id: String, actor: Actor, command: GrantEquipmentCommand): EquipmentView {
        actor.requireAdmin()
        return change(id, actor, command.expectedVersion) { character, session ->
            val base = equipment.findById(checkedId(command.equipmentId), session)?.takeUnless { it.deleted } ?: missing()
            val refs = base.modifierDefinitionRefs + base.stockModifierDefinitionRefs
            val snapshot = catalogs.snapshot(refs)
            // Новый предмет — отдельный документ: инвентарь растёт без ограничения по размеру.
            inventory.add(character._id, CharacterEquipments.fromEquipment(base, snapshot.catalog, refs.map(snapshot::resolve)), session)
            character
        }
    }
    suspend fun change(id: String, actor: Actor, expectedVersion: Long, touch: Collection<String> = emptyList(),
        body: suspend (Character, ClientSession) -> Character): EquipmentView {
        val changed = transactionExecute("character.command") { session ->
            val old = characters.findById(checkedId(id), session) ?: missing(); actor.own(old)
            checkVersion(old.version, expectedVersion)
            val next = body(hydrate(old, touch, session), session)
            validate(next, session)
            characters.update(next, session)
            next
        }
        return view(changed)
    }
}
