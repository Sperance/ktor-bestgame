package ru.descend

import configureModules
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun testRoot() = testApplication {
//        application {
//            DatabaseConfig.init()
//            configureModules()
//            simpleTestData()
//        }
//
//        val timeMillis = measureTimeMillis {
//            repeat(30) {
//                client.get("/user/1/testupdate").apply {
//                    assertEquals(HttpStatusCode.OK, status)
//                }
//            }
//        }
//
//        println("Execution time: $timeMillis ms")
//        println("Execution time: ${timeMillis / 1000.0} seconds")
    }

}