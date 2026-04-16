package features.characters

import application.model.ItemStock
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.annotations.Required
import base.model.BaseEntity
import base.table.BaseTable
import features.property.PropertyCache
import features.user.UsersTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.jsonb

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
        stock.add(ParamsStock(PropertyCache.getFromCode("HEALTH")!!.id, 100.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("STR")!!.id, 1.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("AGI")!!.id, 1.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("INT")!!.id, 1.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("INV")!!.id, 10.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("CRIT")!!.id, 200.0))
        stock.add(ParamsStock(PropertyCache.getFromCode("SPD")!!.id, 1.5))
        return stock
    }
}

@Serializable
data class Character(
    @ReadOnly
    override val id: Long = -1,

    @Required
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
) : BaseEntity