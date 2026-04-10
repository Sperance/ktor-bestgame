package ru.descend

import config.DatabaseFactory
import configureModules
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun testRoot() = testApplication {
        application {
            DatabaseFactory.init()
            configureModules()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}