package ru.descend

import config.DatabaseFactory
import configureModules
import io.ktor.server.testing.testApplication
import kotlin.test.Test

class UserTest {

    @Test
    fun testRoot() = testApplication {
        application {
            DatabaseFactory.init()
            configureModules()
        }
    }

}