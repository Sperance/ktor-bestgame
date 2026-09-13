package ru.descend.features.character.persistence

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.CharacterItems
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.features.user.model.User
import ru.descend.features.user.persistence.UserRepository
import ru.descend.infrastructure.cache.ItemsCache
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.mongo.UniqueIndexConfig
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.CONST_USER_MAX_CHARACTERS
import ru.descend.shared.error.model.CharacterExceptions

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: EquipmentRepository by inject()
    val itemsRepository: ItemsRepository by inject()
    val redemptionCodesRepository: RedemptionCodesRepository by inject()
    val itemsCache: ItemsCache by inject()
    private val modifierCatalogs: ru.descend.features.poe.persistence.MongoModifierCatalog by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: Character, session: ClientSession) {
        if (entity.name.isEmpty()) throw CharacterExceptions.funExceptionName("validateBeforeInsert")
        if (findByField(Character::name, entity.name) != null) throw CharacterExceptions.funExceptionNameDuplicate("validateBeforeInsert", entity.name)
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId)
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        userRepository.update(findedUser, session)
    }

    suspend fun getEquipmentsData(characterId: String): List<Equipment> {
        val character = findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("getEquipmentsData", characterId)
        val ids = character.equipments.map { it.equipmentId }
        return if (ids.isEmpty()) emptyList() else equipmentRepository.findByFilter(Filters.`in`(CONST_FIELD_ID, ids))
    }

    suspend fun getEquippedData(characterId: String): List<Equipment> {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("getEquippedData", characterId)
        val mapIdEquipments = character.equipments.map { it.equipmentId }
        if (mapIdEquipments.isEmpty()) return emptyList()
        return equipmentRepository.findByFilter(
            Filters.`in`(CONST_FIELD_ID, mapIdEquipments)
        )
    }

    /**
     * Добавление нового предмета в инвентарь персонажа. Создание предмета
     */
    suspend fun itemToInventory(characterId: String, item: CharacterEquipments): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("itemToInventory", characterId)
        val template = equipmentRepository.findById(item.equipmentId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("itemToInventory", item.equipmentId)

        // PoE instances are created by the server; callers cannot inject a crafted snapshot.
        val snapshot = modifierCatalogs.snapshot(template.modifierDefinitionRefs + template.stockModifierDefinitionRefs)
        val instance = CharacterEquipments.fromEquipment(template, snapshot.catalog,
            (template.modifierDefinitionRefs + template.stockModifierDefinitionRefs).map(snapshot::resolve))
        require(character.equipments.none { it.uuid == instance.uuid }) { "Duplicate equipment UUID" }
        character.equipments.add(instance)
        transactionExecute("itemToInventory") { session ->
            update(character, session)
        }
        return "Success"
    }

    /**
     * Добавление\удаление предмета из инвентаря персонажа
     */
    suspend fun addItem(characterId: String, itemObj: List<CharacterItems>): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("addItem", characterId)

        val allItems = itemsCache.getCache()

        var isChanged = false
        itemObj.forEach { itm ->
            if (itm.amount == 0L) return@forEach
            if (itm.amount > 100000000L) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (itm.amount < -100000000L) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (allItems.find { it._id == itm.itemId } == null) throw CharacterExceptions.funExceptionItemNotFound("addItem", itm.toString())

            val findedItem = character.items.find { it.itemId == itm.itemId }
            if (findedItem != null) {
                findedItem.amount += itm.amount
                if (findedItem.amount < 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
            }
            else {
                if (itm.amount <= 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
                character.items.add(CharacterItems(itm.itemId, itm.amount))
            }

            isChanged = true
        }

        if (!isChanged) {
            return "Success. No changes"
        }

        //Зачем хранить id предмета без кол-ва
        character.items.removeAll { it.amount == 0L }

        transactionExecute("addItem") { session ->
            update(character, session)
        }
        return "Success"
    }
}
