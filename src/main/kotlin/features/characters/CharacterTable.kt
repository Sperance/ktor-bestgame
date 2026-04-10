package features.characters

import application.enums.EnumStatBool
import application.enums.EnumStatKey
import application.model.ItemStock
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.model.BaseEntity
import base.table.BaseTable
import features.user.UsersTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.collections.plusAssign
import kotlin.compareTo

object CharacterTable : BaseTable("character") {
    val name = varchar("name", 20)
    val description = varchar("description", 500)
    val userId = long("user_id").references(UsersTable.id)

    val level = short("level").default(1)
    val experience = integer("experience").default(0)
    val money = ulong("money").default(0u)

    val params = jsonb<MutableSet<ParamsStock>>(
        name = "params",
        jsonConfig = Json
    ).default(mutableSetOf())

    val buffs = jsonb<MutableSet<Stat>>(
        name = "buffs",
        jsonConfig = Json
    ).default(mutableSetOf())

    val bools = jsonb<MutableSet<StatBool>>(
        name = "bools",
        jsonConfig = Json
    ).default(mutableSetOf())

    /**
     * Инвентарь обычных предметов (золото, ресурсы, зелья).
     * Хранится как JSONB: ["1:100", "2:5"]
     * Ключ = ссылка на таблицу Items, значение = кол-во
     * Экипировка тут НЕ хранится — для неё отдельная таблица equipment.
     */
    val inventory = jsonb<MutableSet<ItemStock>>(
        name = "inventory",
        jsonConfig = Json
    ).default(mutableSetOf())

    /**
     * Получение начальных параметров нового персонажа
     * @return [MutableSet] из элементов [ParamsStock]
     */
    fun getStockParams(): MutableSet<ParamsStock> {
        val stock = mutableSetOf<ParamsStock>()
        stock.add(ParamsStock(EnumStatKey.LIFE, 100.0))
        stock.add(ParamsStock(EnumStatKey.STR, 1.0))
        stock.add(ParamsStock(EnumStatKey.DEX, 1.0))
        stock.add(ParamsStock(EnumStatKey.INT, 1.0))
        stock.add(ParamsStock(EnumStatKey.INVENTORY_SIZE, 10.0))
        stock.add(ParamsStock(EnumStatKey.CRIT_DAMAGE, 200.0))
        stock.add(ParamsStock(EnumStatKey.ATTACK_SPEED, 1.5))
        return stock
    }
}

@Serializable
data class Character(
    @ReadOnly
    override val id: Long? = null,

    val name: String = "",

    val description: String = "",

    val level: Short = 1,

    val experience: Int = 0,

    val money: ULong = 0u,

    var params: MutableSet<ParamsStock> = CharacterTable.getStockParams(),

    var buffs: MutableSet<Stat> = mutableSetOf(),

    var bools: MutableSet<StatBool> = mutableSetOf(),

    var inventory: MutableSet<ItemStock> = mutableSetOf(),

    @Immutable
    val userId: Long = -1,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity {

    /**
     * Получение параметра [bools] по ключу (если он существует)
     * @param enum [EnumStatBool] ключ параметра как Enum
     * @return [StatBool] если элемент найден, иначе NULL
     */
    fun getStat(enum: EnumStatBool): StatBool? {
        return bools.firstOrNull { it.key == enum }
    }

    /**
     * Установка значения параметра [bools] (или добавления, если параметра нет)
     * @param value нужный параметр
     */
    fun setStat(value: StatBool) {
        val currentItems = bools.toMutableSet()

        //Если параметр уже есть - обновляем, если нет - добавляем
        currentItems.find { it.key == value.key }?.
        let { tt ->
            tt.value = value.value
        }?:currentItems.add(value)

        bools = currentItems
    }

    /**
     * Получение параметра [params] по ключу (если он существует)
     * @param enum [EnumStatKey] ключ параметра как Enum
     * @return [ParamsStock] если элемент найден, иначе NULL
     */
    fun getStat(enum: EnumStatKey): ParamsStock? {
        return params.firstOrNull { it.param == enum }
    }

    /**
     * Установка значения параметра (или добавления, если параметра нет)
     * @param value [ParamsStock] нужный параметр
     * @param minVal итоговое значение параметра не сможет быть ниже указанного
     * @param maxVal итоговое значение параметра не сможет быть выше указанного
     * @throws [Exception] Если [minVal] больше чем [maxVal]
     */
    fun setStat(value: ParamsStock, minVal: Double? = null, maxVal: Double? = null) {
        if (minVal != null && maxVal != null && minVal > maxVal) throw Exception("В методе setStat параметр minVal не может быть больше, чем параметр maxVal")

        val currentItems = params.toMutableSet()

        //Если параметр уже есть - обновляем, если нет - добавляем
        currentItems.find { it.param == value.param }?.
        let { tt ->
            tt.value = value.value
            if (minVal != null && tt.value < minVal) tt.value = minVal
            if (maxVal != null && tt.value > maxVal) tt.value = maxVal
        }?:currentItems.add(value)

        params = currentItems
    }

    /**
     * Добавление параметра или увеличение значения параметра в [params]
     * @param value [ParamsStock] необходимый параметр для модификации
     */
    fun addStat(value: ParamsStock, maxVal: Double? = null) {
        val currentItems = params.toMutableSet()

        currentItems.find { it.param == value.param }?.
        let { tt ->
            tt.value += value.value
            if (maxVal != null && tt.value > maxVal) tt.value = maxVal
        }?:currentItems.add(value)

        params = currentItems
    }

    /**
     * Уменьшение значения параметра в [params]
     * Параметр не должен быть меньше нуля (пока) и не должен быть полностью удален (всегда),
     * @param value [ParamsStock] элемент для уменьшения с количеством
     * @param minVal итоговое значение параметра не сможет быть ниже указанного
     */
    fun remStat(value: ParamsStock, minVal: Double? = null) {
        //Находим ссылку на нужный параметр
        getStat(value.param)?.let { param ->
            //Уменьшаем значение параметра
            if (param.value > value.value) {
                param.value -= value.value
            } else {
                //Параметр не должен быть меньше нуля (пока) и не должен быть полностью удален (всегда)
                param.value = 0.0
            }
            if (minVal != null && param.value < minVal) param.value = minVal
        }
    }

    /**
     * Добавление предмета в инвентарь
     * @param itemStock предмет и количество
     */
    fun addItem(itemStock: ItemStock) {
        val currentItems = inventory.toMutableSet()

        // Объединение одинаковых типов по количеству
        val mergedItem = currentItems.find { it.item_id == itemStock.item_id }?.let { it.quantity += itemStock.quantity; it }?:itemStock

        currentItems.add(mergedItem)
        inventory = currentItems
    }

    /**
     * Удаление предмета из инвентаря
     * @param itemStock предмет и количество
     */
    fun removeItem(itemStock: ItemStock) {
        val currentItems = inventory.toMutableSet()

        val findedItem = currentItems.find { it.item_id == itemStock.item_id }
        if (findedItem == null) {
            throw Exception("Данного предмета '${itemStock.item_id}' нет в инвентаре")
        }

        if (findedItem.quantity < itemStock.quantity) {
            throw Exception("Недостаточно количества предмета '${itemStock.item_id}'. Есть ${findedItem.quantity}, требуется ${itemStock.quantity}")
        }

        val mergedItem = currentItems.find { it.item_id == itemStock.item_id }?.let { it.quantity -= itemStock.quantity; it }!!
        currentItems.removeIf { it.item_id == mergedItem.item_id }

        if (mergedItem.quantity > 0)
            currentItems.add(mergedItem)

        inventory = currentItems
    }

    /**
     * Проверка, есть ли в инвентаре указанный предмет и количество
     * @param itemStock предмет и количество
     */
    fun checkQuantity(itemStock: ItemStock): Boolean {
        return inventory.toMutableSet().find { it.item_id == itemStock.item_id && it.quantity >= itemStock.quantity } != null
    }

    /**
     * Установка количества по позиции
     * @param itemStock предмет и количество
     */
    fun setItem(itemStock: ItemStock) {
        val currentItems = inventory.toMutableSet()

        //Редактируемый предмет (его может и не быть)
        val findedItem = currentItems.find { it.item_id == itemStock.item_id }

        //Если предмета нет вообще - просто добавляем его
        if (findedItem == null) {
            return addItem(itemStock)
        }

        //Иначе меняем кол-во на нужное и переписываем объект
        findedItem.quantity = itemStock.quantity
        currentItems.removeIf { it.item_id == findedItem.item_id }
        currentItems.add(findedItem)

        inventory = currentItems
    }
}