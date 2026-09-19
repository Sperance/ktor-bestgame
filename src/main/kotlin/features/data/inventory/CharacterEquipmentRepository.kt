package features.data.inventory

import application.enums.EnumCurrencyOrb
import application.enums.EnumRarity
import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.data.character.CharacterRepository
import features.data.equipment.equipment_data.Equipment
import features.logic.currency.CurrencyApplier
import features.logic.currency.CurrencyOutcome
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterEquipmentRepository : BaseRepository<CharacterEquipment>(
    entityClass = CharacterEquipment::class
), KoinComponent {
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val characterRepository: CharacterRepository by inject()

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
     * Применяет валютную сферу к предмету инвентаря.
     *
     * Сфера списывается у персонажа и предмет сохраняется в одной транзакции,
     * поэтому неудачная проверка не съедает сферу.
     *
     * @param orbItemId id предмета-сферы в коллекции `Items`
     */
    suspend fun applyOrb(characterId: String, inventoryId: String, orbItemId: String): CurrencyOutcome {
        val item = findById(inventoryId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("applyOrb", inventoryId)
        if (item.characterId != characterId)
            throw CharacterExceptions.funExceptionItemNotFound("applyOrb", inventoryId)

        val template = equipmentCache.findById(item.equipmentId)
            ?: throw CharacterExceptions.funExceptionEquipmentNotFound("applyOrb", item.equipmentId)

        val orbItem = itemsCache.findById(orbItemId)
            ?: throw CharacterExceptions.funExceptionItemNotFound("applyOrb", orbItemId)
        val orb = orbItem.takeIf { it.category == EnumCurrencyOrb.CATEGORY }
            ?.let { EnumCurrencyOrb.byCode(it.subCategory) }
            ?: throw CurrencyExceptions.funExceptionNotCurrency("applyOrb", orbItem.name)

        val character = characterRepository.findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("applyOrb", characterId)

        return transactionExecute("applyOrb ${orb.name}") { session ->
            // Сначала списываем сферу: если её нет, предмет даже не трогаем
            characterRepository.spendItem(character, orbItemId, 1, session)

            val outcome = CurrencyApplier.apply(orb, item, template)
            update(outcome.item, session)
            outcome.created?.let { insert(it, session) }
            outcome
        }
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

    /**
     * Проставляет редкость экземплярам, созданным до того, как она у них появилась.
     *
     * До появления сфер редкость жила только на шаблоне, поэтому у старых
     * документов поля нет и оно читается как COMMON. Совместимость разовая.
     *
     * @param equipmentIds шаблоны, чью редкость нужно проставить
     * @return количество обновлённых документов
     */
    suspend fun backfillRarity(equipmentIds: Collection<String>, rarity: EnumRarity, session: ClientSession): Long {
        if (equipmentIds.isEmpty()) return 0

        return collection.updateMany(
            session,
            Filters.and(
                Filters.`in`("equipmentId", equipmentIds),
                Filters.exists("rarity", false)
            ),
            Updates.set("rarity", rarity.name)
        ).modifiedCount
    }

    /**
     * Удаляет предметы с модификаторами старого формата - одиночным полем `value`
     * вместо списка `values`, появившегося вместе с составными модификаторами.
     *
     * Такие документы уже не читаются драйвером, поэтому вычистить их можно
     * только фильтром по сырому полю. Совместимость разовая: когда база
     * пересеяна, метод перестаёт что-либо находить и его можно убрать.
     *
     * @return количество удалённых документов
     */
    suspend fun deleteLegacyParams(session: ClientSession): Long =
        collection.deleteMany(session, Filters.exists("params.value", true)).deletedCount
}
