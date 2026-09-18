package features.data.inventory

import base.exception.model.CharacterExceptions
import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.data.equipment.equipment_data.Equipment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterEquipmentRepository : BaseRepository<CharacterEquipment>(
    entityClass = CharacterEquipment::class
), KoinComponent {
    private val equipmentCache: EquipmentCache by inject()

    init {
        initialize(indexedFields = listOf("characterId", "equipmentId"))
    }

    override suspend fun validateBeforeInsert(entity: CharacterEquipment, session: ClientSession) {
        if (equipmentCache.findById(entity.equipmentId) == null)
            throw CharacterExceptions.funExceptionEquipmentNotFound("validateBeforeInsert", entity.equipmentId)
    }

    /**
     * Весь инвентарь персонажа.
     */
    suspend fun findByCharacter(characterId: String): List<CharacterEquipment> =
        findByFilter(Filters.eq("characterId", characterId))

    /**
     * Только надетые предметы персонажа.
     */
    suspend fun findEquipped(characterId: String): List<CharacterEquipment> =
        findByFilter(
            Filters.and(
                Filters.eq("characterId", characterId),
                Filters.ne("equippedSlot", null)
            )
        )

    /**
     * Создаёт новый экземпляр предмета из шаблона и кладёт его в инвентарь персонажа.
     */
    suspend fun addFromEquipment(
        characterId: String,
        equipment: Equipment,
        session: ClientSession
    ): CharacterEquipment = insert(CharacterEquipment.fromEquipment(characterId, equipment), session)

    /**
     * Надевает предмет в его слот, снимая предмет, который уже занимает этот слот.
     */
    suspend fun equip(characterId: String, inventoryId: String): CharacterEquipment {
        val item = findById(inventoryId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("equip", inventoryId)
        if (item.characterId != characterId)
            throw CharacterExceptions.funExceptionItemNotFound("equip", inventoryId)

        val template = equipmentCache.findById(item.equipmentId)
            ?: throw CharacterExceptions.funExceptionEquipmentNotFound("equip", item.equipmentId)

        return transactionExecute("equip") { session ->
            findEquipped(characterId)
                .filter { it.equippedSlot == template.slot && it._id != item._id }
                .forEach { occupied ->
                    occupied.equippedSlot = null
                    update(occupied, session)
                }

            item.equippedSlot = template.slot
            update(item, session)
            item
        }
    }

    /**
     * Снимает предмет, оставляя его в инвентаре.
     */
    suspend fun unequip(characterId: String, inventoryId: String): CharacterEquipment {
        val item = findById(inventoryId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("unequip", inventoryId)
        if (item.characterId != characterId)
            throw CharacterExceptions.funExceptionItemNotFound("unequip", inventoryId)

        item.equippedSlot = null
        transactionExecute("unequip") { session ->
            update(item, session)
        }
        return item
    }

    /**
     * Количество предметов в инвентаре персонажа.
     */
    suspend fun countByCharacter(characterId: String, session: ClientSession): Long =
        count(session, Filters.eq("characterId", characterId))

    /**
     * Удаляет весь инвентарь персонажа - вызывается при удалении самого персонажа.
     */
    suspend fun deleteByCharacter(characterId: String, session: ClientSession) {
        collection.deleteMany(session, Filters.eq("characterId", characterId))
    }

    /**
     * Удаляет предметы, ссылающиеся на уже несуществующие шаблоны экипировки.
     *
     * @param equipmentIds id всех актуальных шаблонов
     * @return количество удалённых документов
     */
    suspend fun deleteByMissingEquipment(equipmentIds: Collection<String>, session: ClientSession): Long =
        collection.deleteMany(session, Filters.nin("equipmentId", equipmentIds)).deletedCount
}
