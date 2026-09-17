package features.icons

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.*
import kotlinx.serialization.json.*
import ru.descend.domain.icons.IconCatalog
import ru.descend.features.icons.http.iconRoutes
import ru.descend.infrastructure.http.configureSerialization

/** HTTP-контракт набора иконок для ExileForge: манифест, таблицы, картинки и кэширование. */
class IconRouteTest {

    private fun test(block: suspend io.ktor.client.HttpClient.() -> Unit) = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureSerialization(); routing { iconRoutes() } }
        client.block()
    }

    @Test fun manifestListsTheWholeSetAndSupportsFilters() = test {
        val body = Json.parseToJsonElement(get("/api/v1/icons").bodyAsText()).jsonObject
        assertTrue(body["success"]!!.jsonPrimitive.boolean)
        val data = body["data"]!!.jsonObject
        assertEquals(IconCatalog.version, data["version"]!!.jsonPrimitive.content)
        assertEquals(IconCatalog.icons.size, data["total"]!!.jsonPrimitive.int)
        val first = data["icons"]!!.jsonArray.first().jsonObject
        assertTrue(first["url"]!!.jsonPrimitive.content.startsWith("/api/v1/icons/"))
        assertTrue(first["tint"]!!.jsonPrimitive.content.startsWith("#"))

        val weapons = Json.parseToJsonElement(get("/api/v1/icons?category=WEAPON").bodyAsText())
            .jsonObject["data"]!!.jsonObject["icons"]!!.jsonArray
        assertTrue(weapons.isNotEmpty())
        assertTrue(weapons.all { it.jsonObject["id"]!!.jsonPrimitive.content.startsWith("weapon-") })
        assertEquals(HttpStatusCode.BadRequest, get("/api/v1/icons?category=NOPE").status)
    }

    @Test fun bindingsCoverModifiersAndEnums() = test {
        val data = Json.parseToJsonElement(get("/api/v1/icons/bindings").bodyAsText()).jsonObject["data"]!!.jsonObject
        val modifiers = data["modifiers"]!!.jsonObject
        assertTrue(modifiers.size > 200, "Иконка нужна каждому модификатору каталога: ${modifiers.size}")
        assertTrue(modifiers.values.all { IconCatalog.exists(it.jsonPrimitive.content) })
        assertTrue(data["weapons"]!!.jsonObject.containsKey("BOW"))
        assertTrue(data["currencies"]!!.jsonObject.containsKey("CHAOS"))
        assertTrue(data["stats"]!!.jsonObject.containsKey("base_maximum_life"))
    }

    @Test fun iconIsServedAsCacheableSvg() = test {
        val response = get("/api/v1/icons/weapon-sword.svg")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/svg+xml", response.contentType()?.withoutParameters()?.toString())
        val etag = assertNotNull(response.headers[HttpHeaders.ETag])
        assertTrue(response.headers[HttpHeaders.CacheControl]!!.contains("max-age"))
        assertTrue(response.bodyAsText().startsWith("<svg"))

        assertEquals(HttpStatusCode.NotModified, get("/api/v1/icons/weapon-sword.svg") {
            header(HttpHeaders.IfNoneMatch, etag)
        }.status)
        assertEquals(HttpStatusCode.OK, get("/api/v1/icons/weapon-sword.svg?variant=plain").status)
        assertEquals(HttpStatusCode.BadRequest, get("/api/v1/icons/weapon-sword.svg?variant=neon").status)
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/icons/not-an-icon.svg").status)
        assertEquals(HttpStatusCode.NotFound, get("/api/v1/icons/weapon-sword").status)
    }

    @Test fun spriteIsServedOnce() = test {
        val response = get("/api/v1/icons/sprite.svg")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<symbol id=\"icon-stat-life\""))
        assertEquals(HttpStatusCode.NotModified,
            get("/api/v1/icons/sprite.svg") { header(HttpHeaders.IfNoneMatch, response.headers[HttpHeaders.ETag]!!) }.status)
    }
}
