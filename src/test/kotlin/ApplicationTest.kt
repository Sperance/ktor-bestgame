package ru.descend

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import kotlin.test.*
import kotlinx.serialization.json.*
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.string
import ru.descend.infrastructure.http.AppJson
import ru.descend.infrastructure.http.configureSerialization

/** Exercises the real JSON plugin and the legacy polymorphic discriminator used by ExileForge. */
class ApplicationTest {
    @Test fun equipmentWireTypesSurvivePackageMigration() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            configureSerialization()
            routing { post("/contract") { call.respond(call.receive<Equipment>()) } }
        }
        val catalog = PoeCatalog.bundled
        for ((name, type) in listOf("Rusted Sword" to "Weapon", "Iron Hat" to "Armor", "Iron Ring" to "Accessory")) {
            val id = catalog.bases.entries.first { it.value.string("name") == name }.key
            val item = catalog.equipment(id)
            val request = AppJson.encodeToString(Equipment.serializer(), item)
            assertEquals("features.data.equipment.equipment_data.$type", Json.parseToJsonElement(request).jsonObject["type"]!!.jsonPrimitive.content)
            val response = client.post("/contract") { contentType(ContentType.Application.Json); setBody(request) }
            assertEquals(HttpStatusCode.OK, response.status)
            val decoded = AppJson.decodeFromString(Equipment.serializer(), response.bodyAsText())
            assertEquals(item._id, decoded._id)
            assertEquals(item.stockModifierDefinitionRefs, decoded.stockModifierDefinitionRefs)
        }
    }
}
