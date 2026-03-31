package application.data.inventory

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.json.jsonb
import application.data.characters.CharacterEntity
import application.data.characters.CharactersTable
import application.data.equipments.EquipmentEntity
import application.data.equipments.SnapshotEquipment
import application.model.ItemStock
import application.enums.EnumStatKey
import extensions.ResultExec
import application.data.BaseEntity
import application.data.BaseTable
import kotlin.time.toKotlinInstant

object InventoryTable : BaseTable("inventory") {
    val character = reference("character", CharactersTable).uniqueIndex()

    val items = jsonb<MutableSet<ItemStock>>(
        name = "items",
        jsonConfig = Json
    ).nullable()
}

class InventoryEntity(id: EntityID<Long>) : BaseEntity<SnapshotInventory>(id, InventoryTable) {
    var character by CharacterEntity referencedOn InventoryTable.character
    var items by InventoryTable.items

    override fun toSnapshot(): SnapshotInventory =
        SnapshotInventory(
            _id = id.value,
            _items = items
        ).apply {
            _createdAt = createdAt.toKotlinInstant()
            _updatedAt = updatedAt.toKotlinInstant()
            _deletedAt = deletedAt?.toKotlinInstant()
            _version = version
        }

    override fun toString(): String {
        return "InventoryEntity(character=${character.id}, items=$items)"
    }

    companion object : LongEntityClass<InventoryEntity>(InventoryTable)

    /*****/

    /**
     * Добавление предмета в инвентарь
     * @param itemStock предмет и количество
     */
    fun addItem(itemStock: ItemStock): ResultExec {
        val currentItems = items?.toMutableSet() ?: mutableSetOf()

        var findedItem = false

        // Объединение одинаковых типов по количеству
        val mergedItem = currentItems.find { it.type == itemStock.type }?.let { findedItem = true; it.quantity += itemStock.quantity; it }?:itemStock

        // Проверка на наличие свободного слота - если предмет новый в инвентаре
        if (!findedItem) {
            val checkInventory = checkInventory(itemStock)
            if (checkInventory.isError()) return checkInventory
        }

        currentItems.add(mergedItem)
        items = currentItems
        return ResultExec.Success()
    }

    /**
     * Удаление предмета из инвентаря
     * @param itemStock предмет и количество
     */
    fun removeItem(itemStock: ItemStock): ResultExec {
        val currentItems = items?.toMutableSet()
            ?: return ResultExec.Error("Некорректная инициализация инвентаря персонажа ${character.name}. Объект items NULL")

        val findedItem = currentItems.find { it.type == itemStock.type }
        if (findedItem == null) {
            return ResultExec.Error("Данного предмета '${itemStock.type.text}' нет в инвентаре")
        }

        if (findedItem.quantity < itemStock.quantity) {
            return ResultExec.Error("Недостаточно количества предмета '${itemStock.type.text}'. Есть ${findedItem.quantity}, требуется ${itemStock.quantity}")
        }

        val mergedItem = currentItems.find { it.type == itemStock.type }?.let { it.quantity -= itemStock.quantity; it }!!
        currentItems.removeIf { it.type == mergedItem.type }

        if (mergedItem.quantity > 0)
            currentItems.add(mergedItem)

        items = currentItems

        return ResultExec.Success()
    }

    /**
     * Проверка, есть ли в инвентаре указанный предмет и количество
     * @param itemStock предмет и количество
     */
    fun checkQuantity(itemStock: ItemStock): Boolean {
        return items?.toMutableSet()?.find { it.type == itemStock.type && it.quantity >= itemStock.quantity } != null
    }

    /**
     * Установка количества по позиции
     * @param itemStock предмет и количество
     */
    fun setItem(itemStock: ItemStock): ResultExec {
        val currentItems = items?.toMutableSet() ?: mutableSetOf()

        //Редактируемый предмет (его может и не быть)
        val findedItem = currentItems.find { it.type == itemStock.type }

        //Если предмета нет вообще - просто добавляем его
        if (findedItem == null) {
            return addItem(itemStock)
        }

        //Иначе меняем кол-во на нужное и переписываем объект
        findedItem.quantity = itemStock.quantity
        currentItems.removeIf { it.type == findedItem.type }
        currentItems.add(findedItem)

        items = currentItems

        return ResultExec.Success()
    }

    /**
     * Проверка инвентаря перед добавлением нового предмета
     * @param itemStock предмет, который нужно добавить
     */
    private fun checkInventory(itemStock: ItemStock): ResultExec {
        val invSize = character.getStat(EnumStatKey.INVENTORY_SIZE)
            ?: throw Exception("Некорректная инициализация персонажа ${character.name}. Не найден параметр INVENTORY_SIZE")

        if (getSize() + 1 > invSize.value)
            return ResultExec.Error("Недостаточно места в инвентаре для добавления нового предмета '${itemStock.type.text}'")

        return ResultExec.Success()
    }

    /**
     * Получить размер заполненного инвентаря (с экипировкой)
     * @return заполнено слотов
     */
    fun getSize(): Int {
        val equipmentSize = character.getEquipments().size
        return (items?.size?.plus(equipmentSize)) ?: 0
    }

    /**
     * Проверка инвентаря на пустоту
     */
    fun isEmpty(): Boolean {
        return getSize() == 0
    }

    /**
     * Очистка всего инвентаря
     */
    fun clearInventory() {
        items = mutableSetOf()
    }

    /*****/

    /**
     * Добавление предмета в инвентарь персонажа
     * @param item предмет, который необходимо добавить в инвентарь
     */
    fun addEquipment(item: SnapshotEquipment): ResultExec {

        val checkInventory = checkInventory(item.toItemStock())
        if (checkInventory.isError()) return checkInventory

        EquipmentEntity.new {
            this.character = this@InventoryEntity.character

            this.name = item._name
            this.content = item._content
            this.enumEquipmentType = item._enumEquipmentType

            this.requirements = item._requirements
            this.params = item._params
            this.buffs = item._buffs
            this.bools = item._bools
        }

        return ResultExec.Success()
    }

    fun getEquipments(): List<EquipmentEntity> {
        return character.getEquipments()
    }
}