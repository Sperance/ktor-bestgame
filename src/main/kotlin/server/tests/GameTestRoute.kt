package server.tests

import application.DatabaseConfig.dbQuery
import application.data.characters.DAOCharacters
import application.data.equipments.SnapshotEquipment
import application.data.users.DAOusers
import application.model.ItemStock
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import application.enums.EnumEquipmentType
import application.enums.EnumItem
import application.enums.EnumStatBool
import application.enums.EnumStatKey
import application.enums.EnumStatType
import extensions.printLog
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
suspend fun simpleTestData() {
    val userDao = DAOusers()
    val characterDao = DAOCharacters()

    dbQuery { tr ->
        val user = userDao.create {
            name = "John${System.currentTimeMillis()}"
            email = "john@example.com"
        }

        printLog("CREATING CHAR")
        characterDao.create {
            name = "Deascend"
            this.user = user
        }

        val charEnt = characterDao.create {
            name = "ATLANT"
            params = getStockParams()
            buffs = mutableSetOf(Stat(EnumStatKey.LIFE, EnumStatType.FLAT, 200.0), Stat(EnumStatKey.LIFE, EnumStatType.PERCENT, 20.0), Stat(EnumStatKey.ATTACK_SPEED, EnumStatType.FLAT,2.0))
            bools = mutableSetOf(StatBool(EnumStatBool.IS_BANNED, true), StatBool(EnumStatBool.IS_ALIVE, false))
            this.user = user
        }

        charEnt.setStat(StatBool(EnumStatBool.IS_BANNED, false))
        charEnt.setStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 20.3))
        charEnt.setStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 40.1))

        charEnt.setStat(ParamsStock(EnumStatKey.DEX, 3.0))
        charEnt.addStat(ParamsStock(EnumStatKey.DEX, 0.75))

        charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 0.1))
        charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 39.0))
        charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 1.0))

        printLog(charEnt.getInventory().addItem(ItemStock(EnumItem.RESOURCE_WD, 400)))
        printLog(charEnt.getInventory().addItem(ItemStock(EnumItem.RESOURCE_WD, 4)))

        printLog(charEnt.getInventory().removeItem(ItemStock(EnumItem.RESOURCE_WD, 1)))
        printLog("INV: ${charEnt.getInventory()}")

        printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 403)))
        printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 404)))
        printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 0)))
        printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 1000)))

        printLog(charEnt.getInventory().setItem(ItemStock(EnumItem.RESOURCE_WD, 10)))
        printLog(charEnt.getInventory().setItem(ItemStock(EnumItem.ITEM_POTION_HEALTH, 4)))

        repeat(10) { counter ->
            printLog(charEnt.getInventory().addEquipment(
                SnapshotEquipment(
                    _name = "name$counter",
                    _content = "content$counter",
                    _enumEquipmentType = EnumEquipmentType.GLOVES
                )
            ))
        }

        val charSnap = charEnt.toSnapshot()

        val res1 = charSnap.calculateParamsWithBuffs()
        printLog("res1: $res1")

        val res2 = charSnap.calculateParamsWithBuffs()
        printLog("res2: $res2")

        printLog(":::EQUIPMENTS:::")
        printLog("${charEnt.getEquipments()}")

        printLog(":::EQUIPMENTS:::")
        printLog("${charEnt.getEquipments()}")
    }
}

@OptIn(ExperimentalUuidApi::class)
fun Application.configureTestRouting() {
    routing {
        route("/test") {
            get("/1") {
                val userDao = DAOusers()
                val characterDao = DAOCharacters()

                dbQuery { tr ->
                    val user = userDao.create {
                        name = "John${System.currentTimeMillis()}"
                        email = "john@example.com"
                    }

                    printLog("CREATING CHAR")
                    characterDao.create {
                        name = "Deascend"
                        this.user = user
                    }

                    val charEnt = characterDao.create {
                        name = "ATLANT"
                        params = getStockParams()
                        buffs = mutableSetOf(Stat(EnumStatKey.LIFE, EnumStatType.FLAT, 200.0), Stat(EnumStatKey.LIFE, EnumStatType.PERCENT, 20.0), Stat(EnumStatKey.ATTACK_SPEED, EnumStatType.FLAT,2.0))
                        bools = mutableSetOf(StatBool(EnumStatBool.IS_BANNED, true), StatBool(EnumStatBool.IS_ALIVE, false))
                        this.user = user
                    }

                    charEnt.setStat(StatBool(EnumStatBool.IS_BANNED, false))
                    charEnt.setStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 20.3))
                    charEnt.setStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 40.1))

                    charEnt.setStat(ParamsStock(EnumStatKey.DEX, 3.0))
                    charEnt.addStat(ParamsStock(EnumStatKey.DEX, 0.75))

                    charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 0.1))
                    charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 39.0))
                    charEnt.remStat(ParamsStock(EnumStatKey.MAGIC_RESIST, 1.0))

                    printLog(charEnt.getInventory().addItem(ItemStock(EnumItem.RESOURCE_WD, 400)))
                    printLog(charEnt.getInventory().addItem(ItemStock(EnumItem.RESOURCE_WD, 4)))

                    printLog(charEnt.getInventory().removeItem(ItemStock(EnumItem.RESOURCE_WD, 1)))
                    printLog("INV: ${charEnt.getInventory()}")

                    printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 403)))
                    printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 404)))
                    printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 0)))
                    printLog(charEnt.getInventory().checkQuantity(ItemStock(EnumItem.RESOURCE_WD, 1000)))

                    printLog(charEnt.getInventory().setItem(ItemStock(EnumItem.RESOURCE_WD, 10)))
                    printLog(charEnt.getInventory().setItem(ItemStock(EnumItem.ITEM_POTION_HEALTH, 4)))

                    repeat(10) { counter ->
                        printLog(charEnt.getInventory().addEquipment(
                            SnapshotEquipment(
                                _name = "name$counter",
                                _content = "content$counter",
                                _enumEquipmentType = EnumEquipmentType.GLOVES
                            )
                        ))
                    }

                    val charSnap = charEnt.toSnapshot()

                    val res1 = charSnap.calculateParamsWithBuffs()
                    printLog("res1: $res1")

                    val res2 = charSnap.calculateParamsWithBuffs()
                    printLog("res2: $res2")

                    printLog(":::EQUIPMENTS:::")
                    printLog("${charEnt.getEquipments()}")

                    printLog(":::EQUIPMENTS:::")
                    printLog("${charEnt.getEquipments()}")
                }
                call.respondText("It`s ok!")
            }
        }
    }
}
