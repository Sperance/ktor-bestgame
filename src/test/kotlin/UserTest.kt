package ru.descend

import config.DatabaseFactory
import configureModules
import features.characters.CharacterTable
import features.equipment.EquipmentTable
import features.items.ItemsTable
import features.stats.CharacterStatsTable
import features.user.UsersTable
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun testRoot() = testApplication {
        application {
            DatabaseFactory.init(tables = arrayOf(
                UsersTable,
                CharacterTable,
                EquipmentTable,
                ItemsTable,
                CharacterStatsTable))
            configureModules()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}